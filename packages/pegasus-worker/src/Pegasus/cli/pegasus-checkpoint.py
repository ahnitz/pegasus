#!/usr/bin/env python3
import argparse
import logging
import logging.handlers
import os
import re
import signal
import sys
import tarfile
import threading
import time
from typing import Iterable, List, Set

log = logging.getLogger()

# name of archived & compressed checkpoints
CHECKPOINT_FILENAME = "pegasus.checkpoint.tar.gz"

# pegasus-transfer url file expected to be generated by pegasus-lite
PEGASUS_TRANSFER_URL_FILE = "pegasus_checkpoint_transfer_urls.json"

# file to which this process's PID will be written upon startup
PEGASUS_CHECKPOINT_PID_FILE = "pegasus_checkpoint.pid"


def parse_args(args: List[str] = sys.argv[1:]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Periodically transfer checkpoints back to staging site."
    )

    parser.add_argument(
        "-p",
        "--pattern",
        action="append",
        dest="patterns",
        help="""regex patterns to match when searching for files to checkpoint""",
    )

    def validate_interval(value: str) -> int:
        """
        Ensure that given interval is an integer and is nonzero.

        :param value: the given interval to validate
        :type value: str
        :raises argparse.ArgumentTypeError: interval cannot be casted to an int or is a negative integer
        :return: the given value
        :rtype: int
        """
        is_valid = True
        try:
            value = int(value)
        except ValueError:
            is_valid = False

        if not is_valid or value < 0:
            raise argparse.ArgumentTypeError(
                "interval {} must be a nonnegative integer".format(value)
            )

        return value

    parser.add_argument(
        "-i",
        "--interval",
        type=validate_interval,
        metavar="SECONDS",
        help="""interval in seconds at which pegasus-checkpoint will send
                checkpoint file back to the staging site
                """,
    )

    parser.add_argument(
        "-d", "--debug", action="store_true", default=False, help="enable debug logging"
    )

    parser.add_argument(
        "-l",
        "--log-to-file",
        action="store_true",
        default=False,
        help="enable logging to file pegasus-checkpoint.log",
    )

    return parser.parse_args(args)


def configure_logging(level: int, log_to_file: bool):
    """
    Setup logging.

    :param level: log level to use; set to :code:`logging.DEBUG` to enable debug logging
    :type level: int
    :param log_to_file: whether or not to write logs to the file pegasus-checkpoint.log
    :type log_to_file: bool
    """
    if level == logging.DEBUG:
        level = logging.DEBUG
    else:
        level = logging.INFO

    handlers = [logging.StreamHandler()]
    if log_to_file:
        handlers.append(
            logging.handlers.RotatingFileHandler(
                filename="pegasus-checkpoint.log",
                mode="a",
                maxBytes=(1 << 20),
                backupCount=2,
            )
        )

    logging.basicConfig(
        level=level, format="%(asctime)s [%(levelname)s] %(message)s", handlers=handlers
    )


def write_pid():
    """Write PID to :code:`PEGASUS_CHECKPOINT_PID_FILE`"""
    with open(PEGASUS_CHECKPOINT_PID_FILE, "w") as f:
        f.write(os.getpid())


class PeriodicCheckpointNotifier(threading.Thread):
    def __init__(self, interval: int, notify: threading.Event):
        """Constructor

        :param interval: interval in seconds to sleep for
        :type interval: int
        :param notify: event to set, which will notify checkpoint worker thread to wake up and run 
        :type notify: threading.Event
        """
        super().__init__(group=None, target=None, name="PeriodicCheckpointNotifier")

        self.interval = interval
        self.notify = notify

    def run(self):
        """
        Repeatedly sleep for the given interval, then notify checkpoint worker
        thread to start working upon waking up.
        """
        while True:
            time.sleep(interval)
            self.notify.set()


class CheckpointWorker(threading.Thread):
    def __init__(self, notify: threading.Event, patterns: List[str]):
        super().__init__(group=None, target=None, name="CheckpointWorker")

        self.notify = notify
        self.patterns = patterns

    def run(self):
        """
        Wait for notification to start working. Once notified, create the
        checkpoint archive file and transfer back to staging site with either
        pegasus-transfer or condor_chirp.
        """
        while True:
            # wait till we are notified
            self.notify.wait(timeout=None)

            # work
            matched_files = CheckpointWorker.get_matched_filenames(self.patterns)
            CheckpointWorker.archive_and_compress(matched_files)
            # TODO: invoke p-transfer or condor_chirp (how will we know which one to call?)

            # clear the notification, so we can wait until it is set again
            self.notify.clear()

    @staticmethod
    def get_matched_filenames(patterns: List[str]) -> Set[str]:
        """
        Get all files/folders in the current working directory that match the
        given file patterns.

        :param patterns: regex patterns to match filenames with
        :type patterns: List[str]
        :return: all files and or folders which match one of the given patterns
        :rtype: Set[str]
        """
        patterns = [re.compile(p) for p in patterns]
        files_in_cwd = [str(f) for f in Path(".").iterdir()]
        matched = Set()

        # n^2.. maybe can do a little better by pulling out files from files_in_cwd (using a set
        # isntead) as we make matches so we don't try to iterate over it again
        for p in patterns:
            for f in files_in_cwd:
                is_match = bool(p.fullmatch(f))

                if is_match:
                    matched.add(f)

                log.debug("pattern: {}, file: {}, match: {}".format(p, f, is_match))

        log.info("given patterns matched the following filenames: {}".format(matched))

        return matched

    @staticmethod
    def archive_and_compress(filenames: Iterable[str]):
        """
        Given a list of file/folder names in CWD, archive and compress them into a single file
        :code:`pegasus.checkpoint.tar.gz`

        :param filenames: list of filenames to archive and compress 
        :type filenames: Iterable[str]
        """
        with tarfile.open(CHECKPOINT_FILENAME, "w|gz") as tar:
            for f in filenames:
                tar.add(name=f, recursive=True)

        checkpoint_size = Path(CHECKPOINT_FILENAME).stat().st_size

        log.info(
            "created {} byte checkpoint file: {}".format(
                checkpoint_size, CHECKPOINT_FILENAME
            )
        )


if __name__ == "__main__":
    args = parse_args()

    log_level = logging.INFO
    if args.debug:
        log_level = logging.DEBUG

    configure_logging(level=log_level, log_to_file=args.log_to_file)

    write_pid()

    # event to signal to worker thread to start saving checkpoints
    notify = threading.Event()

    # main thread to handle signal from user application
    def SIGUSR1_handler(signum, frame):
        notify.set()

    signal.signal(signal.SIGUSR1, SIGUSR1_handler)

    # create and start a periodic checkpoint notifier if user specified an interval to run on
    periodic_notifier = None
    if args.interval:
        periodic_notifier = PeriodicCheckpointNotifier(
            interval=args.interval, notify=notify
        )
        periodic_notifier.start()

    worker = CheckpointWorker(notify=notify, patterns=args.patterns)
    worker.start()

    if periodic_notifier:
        periodic_notifier.join()
    worker.join()