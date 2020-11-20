#!/usr/bin/env python3

import os
import sys
import json
import optparse
import logging
import subprocess
import distutils.spawn

# --- global variables ----------------------------------------------------------------

prog_dir  = os.path.realpath(os.path.join(os.path.dirname(sys.argv[0])))
prog_base = os.path.split(sys.argv[0])[1]   # Name of this program

logger = logging.getLogger("my_logger")
darhan_parser = distutils.spawn.find_executable("darshan-parser")

# --- functions ----------------------------------------------------------------
            
def setup_logger(debug_flag):
    # log to the console
    console = logging.StreamHandler()
    
    # default log level - make logger/console match
    logger.setLevel(logging.INFO)
    console.setLevel(logging.INFO)

    # debug - from command line
    if debug_flag:
        logger.setLevel(logging.DEBUG)
        console.setLevel(logging.DEBUG)

    # formatter
    formatter = logging.Formatter("%(asctime)s %(levelname)7s:  %(message)s")
    console.setFormatter(formatter)
    logger.addHandler(console)
    logger.debug("Logger has been configured")


def prog_sigint_handler(signum, frame):
    logger.warn("Exiting due to signal %d" % (signum))
    sys.exit(1)


#event: darshan.summary
def parse_summary(inputFile):
    summary_obj = {}

    cmd = [darhan_parser, inputFile]

    try:
        darshan_output = subprocess.check_output(cmd)
        darshan_output = darshan_output.split("\n")
    except subprocess.CalledProcessError as err:
        logger.critical(err)
        sys.exit(1)

    # summary region
    k = 0
    for i in xrange(len(darshan_output)):
        line = darshan_output[i]
        if not line:
            k = i + 1
            break

        splitted = line.split(": ")
        key = splitted[0].replace("#", "").strip().replace(" ", "_")
        if not key == "metadata":
            if key in ["start_time", "end_time", "nprocs"]:
                summary_obj[key] = int(splitted[1].strip())
            elif key == "run_time":
                summary_obj[key] = float(splitted[1].strip())
            else:
                summary_obj[key] = splitted[1].strip()
        else:
            if not "metadata" in summary_obj:
                summary_obj[key] = {}
            splitted_tier2 = splitted[1].split(" = ")
            key_tier2 = splitted_tier2[0].strip().replace(" ", "_")
            summary_obj[key][key_tier2] = splitted_tier2[1].strip()
                

    #### log file region
    #for i in xrange(k, len(darshan_output)):
    #    line = darshan_output[i]
    #    if not line:
    #        k = i + 1
    #        break
    #    elif line.startswith("# log file regions"):
    #        continue
    #    elif line.startswith("# --"):
    #        continue
    #
    #    splitted = line.split(": ")
    #    key = splitted[0].replace("#", "").strip().replace(" ", "_")
    #    
    #    value = {}
    #    splitted_tier2 = splitted[1].strip().split()
    #    value["bytes"] = splitted_tier2[0]
    #    value["compressed"] = 0 if "uncompressed" in splitted_tier2[2] else 1
    #    if len(splitted_tier2) == 4:
    #        value["version"] = splitted_tier2[3].split("=")[1]
    #
    #    summary_obj[key] = value
    
    return summary_obj


#event: darshan.total
def parse_total(inputFile):
    total_obj = {}

    cmd = [darhan_parser, "--total", inputFile]

    try:
        darshan_output = subprocess.check_output(cmd)
        darshan_output = darshan_output.split("\n")
    except subprocess.CalledProcessError as err:
        logger.critical(err)
        sys.exit(1)

    for line in darshan_output:
        if line.startswith("#") or not line:
            continue

        splitted = line.split(": ")
        key = splitted[0].replace("#", "").strip().replace(" ", "_")

        total_obj[key] = float(splitted[1])
        
    return total_obj


#event: darshan.perf
def parse_perf(inputFile):
    curr_obj = None
    POSIX_module_data = {"unique_files": {}, "shared_files": {}}
    STDIO_module_data = {"unique_files": {}, "shared_files": {}}

    cmd = [darhan_parser, "--perf", inputFile]
    
    try:
        darshan_output = subprocess.check_output(cmd)
        darshan_output = darshan_output.split("\n")
    except subprocess.CalledProcessError as err:
        logger.error(err)
        sys.exit(1)

    for line in darshan_output:
        if "POSIX module data" in line:
            curr_obj = POSIX_module_data
        elif "STDIO module data" in line:
            curr_obj = STDIO_module_data
        elif curr_obj is None:
            continue

        if line.startswith("# total_bytes"):
            curr_obj["total_bytes"] = int(line.split(": ")[1])
        elif line.startswith("# unique files") or line.startswith("# shared files"):
            splitted = line.split(": ")
            key = splitted[0].replace("#", "").strip().replace(" ", "_")
            curr_obj[key][splitted[1]] = float(splitted[2])
        elif line.startswith("# agg_perf"):
            splitted = line.split(": ")
            key = splitted[0].replace("#", "").strip().replace(" ", "_")
            curr_obj[key] = float(splitted[1])

    perf_obj = {"POSIX_module_data": POSIX_module_data, "STDIO_module_data": STDIO_module_data}

    return perf_obj

def main():
    MONITORING_EVENT_START_MARKER = "@@@MONITORING_PAYLOAD - START@@@"
    MONITORING_EVENT_END_MARKER = "@@@MONITORING_PAYLOAD - END@@@"

    # Configure command line option parser
    prog_usage = "usage: %s [options]" % (prog_base)
    parser = optparse.OptionParser(usage=prog_usage)
    
    parser.add_option("-f", "--file", action = "store", dest = "file", help = "Darshan log file")
    parser.add_option("-d", "--debug", action = "store_true", dest = "debug", help = "Enables debugging output")
    
    # Parse command line options
    (options, args) = parser.parse_args()
    setup_logger(options.debug)
    
    # Check if darshan-parser was found
    if darhan_parser is None:
        logger.critical("darshan-parser couldn't be located !!!")
        sys.exit(1)
    else:
        logger.info("darshan-parser location: %s" % darhan_parser)

    if not options.file:
        logger.critical("An input file has to be given with --file")
        sys.exit(1)
    
    payload_summary = parse_summary(options.file)
    #payload_total = parse_total(inputFile)
    payload_perf = parse_perf(options.file)

    event_payload = payload_summary.copy()
    event_payload.update(payload_perf)

    darshan_event = {
        "ts": int(os.path.getmtime(options.file)),
        "monitoring_event": "darshan.perf",
        "payload": [event_payload]
    }


    print(MONITORING_EVENT_START_MARKER)
    print(json.dumps(darshan_event, sort_keys = True, indent=2))
    print(MONITORING_EVENT_END_MARKER)
    


if __name__ == "__main__":
    main()