/**
 *  Copyright 2007-2008 University Of Southern California
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.isi.pegasus.planner.parser;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.JacksonYAMLParseException;
import com.networknt.schema.JsonSchema;

import edu.isi.pegasus.common.logging.LogManager;
import edu.isi.pegasus.common.logging.LogManagerFactory;
import edu.isi.pegasus.planner.catalog.classes.Profiles;
import edu.isi.pegasus.planner.catalog.classes.SysInfo;
import edu.isi.pegasus.planner.catalog.transformation.TransformationCatalogEntry;
import edu.isi.pegasus.planner.catalog.transformation.classes.Container;
import edu.isi.pegasus.planner.catalog.transformation.classes.Container.TYPE;
import edu.isi.pegasus.planner.catalog.transformation.classes.TCType;
import edu.isi.pegasus.planner.catalog.transformation.classes.TransformationStore;
import edu.isi.pegasus.planner.catalog.transformation.impl.Abstract;
import edu.isi.pegasus.planner.classes.Profile;
import edu.isi.pegasus.planner.common.VariableExpansionReader;
import edu.isi.pegasus.planner.namespace.Namespace;
import edu.isi.pegasus.planner.parser.tokens.TransformationCatalogKeywords;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Parses the input stream and generates the TransformationStore as output.
 *
 * This parser is able to parse the Transformation Catalog specification in the
 * following format
 *
 * <pre>
 - namespace: "ls"
 name: "keg"
 version: 1.0

 profile:
 - environment:
 "APP_HOME": "/tmp/myscratch"
 "JAVA_HOME": "/opt/java/1.6"

 site:
 - name: "isi"
 profile:
 environment:
 "HELLo": "WORLD"
 "JAVA_HOME": "/opt/java/1.6"
 condor:
 "FOO": "bar"
 pfn: /usr/bin/ls
 arch: x86
 osrelease: fc
 osversion: 4
 os_type: INSTALLED

 - name: "ads"
 profile:
 environment:
 "HELLo": "WORLD"
 "JAVA_HOME": "/opt/java/1.6"
 condor:
 "FOO": "bar"
 pfn: /path/to/keg
 arch: x86
 os: linux
 osrelease: fc
 osversion: 4
 os_type: INSTALLED
 c: "centos-pegasus"

 cont:
 - name: "centos-pegasus"
 image: docker:///rynge/montage:latest
 image_site: optional site
 mount: /Volumes/Work/lfs1:/shared-data/:ro
 profile:
 environment:
 "JAVA_HOME": "/opt/java/1.6"

 - namespace: "cat"
 name: "keg"
 version: 1.0

 site:
 - name: "ads"
 profile:
 environment:
 "HELLo": "WORLD"
 "JAVA_HOME": "/opt/java/1.6"
 condor:
 "FOO": "bar"
 pfn: /usr/bin/cat
 arch: x86
 os: linux
 osrelease: fc
 osversion: 4
 os_type: INSTALLED
 </pre>
 *
 * @author Mukund Murrali
 * @version $Revision$
 *
 *
 */
public class TransformationCatalogYAMLParser {

    /**
     * Schema file name;
     *
     */
    private static final String SCHEMA_URI = "http://pegasus.isi.edu/schema/tc-5.0.yml";
    /**
     * Schema File Object;
     *
     */
    private static File SCHEMA_FILENAME = null;

    /**
     * The transformation to the logger used to log messages.
     */
    private LogManager mLogger;

    /**
     * This reader is used for reading the contents of the YAML file
     *
     */
    private Reader mReader;

    /**
     * Initializes the parser with an input stream to read from.
     *
     * @param stream
     * @param schemaDir
     *
     * @param input is the stream opened for reading.
     * @param logger the transformation to the logger.
     *
     * @throws IOException
     * @throws ScannerException
     */
    public TransformationCatalogYAMLParser(Reader stream, File schemaDir, LogManager logger) throws IOException, ScannerException {
        File yamlSchemaDir = new File(schemaDir, "yaml");
        SCHEMA_FILENAME = new File(yamlSchemaDir, new File(SCHEMA_URI).getName());
        mReader = stream;
        mLogger = logger;
    }

    /**
     * Parses the complete input stream
     *
     * @param modifyFileURL Boolean indicating whether to modify the file URL or
     * not
     *
     * @return TransformationStore
     *
     * @throws ScannerException
     * @throws
     */
    @SuppressWarnings("unchecked")
    public TransformationStore parse(boolean modifyFileURL) throws ScannerException {
        TransformationStore store = new TransformationStore();
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(MapperFeature.ALLOW_COERCION_OF_SCALARS, false);
        Object yamlData = null;
        JsonNode root = null;
        try {
            root = mapper.readTree(mReader);

        } catch (JacksonYAMLParseException e) {
            throw new ScannerException(e.getLocation().getLineNr(), parseError(e));
        } catch (Exception e) {
            throw new ScannerException("Error in loading the yaml file " + mReader, e);
        }
        if (root != null) {
            YAMLSchemaValidationResult result = YAMLSchemaValidator.getInstance().validate(root,
                    SCHEMA_FILENAME, "transformation");

            // schema validation is done here.. in case of any validation error we throw the
            // result..
            if (!result.isSuccess()) {
                List<String> errors = result.getErrorMessage();
                StringBuilder errorResult = new StringBuilder();
                int i = 1;
                for (String error : errors) {
                    if (i > 1) {
                        errorResult.append(",");
                    }
                    errorResult.append("Error ").append(i++).append(":{");
                    errorResult.append(error).append("}");
                }
                throw new ScannerException(errorResult.toString());
            }
            JsonNode node = root;
            if (root.has(TransformationCatalogKeywords.TRANSFORMATIONS.getReservedName())) {
                node = node.get(TransformationCatalogKeywords.TRANSFORMATIONS.getReservedName());
                System.out.println(node);
                if (node.isArray()) {
                    for (JsonNode transformation : node) {
                        List<TransformationCatalogEntry> entries = this.createTransformationCatalogEntry(transformation);
                        for(TransformationCatalogEntry entry: entries ){
                            if (modifyFileURL) {
                                store.addEntry(Abstract.modifyForFileURLS(entry));
                            } else {
                                store.addEntry(entry);
                            }
                            // we have information about one transformation catalog c
                            mLogger.log("Transformation Catalog Entry parsed is - " + entry,
                                         LogManager.DEBUG_MESSAGE_LEVEL);
                        }
                    }
                } else {
                    throw new ScannerException("transformations: value should be of type array ");
                }
            }
            if (root.has(TransformationCatalogKeywords.CONTAINERS.getReservedName())) {
                node = root.get(TransformationCatalogKeywords.CONTAINERS.getReservedName());
                System.out.println(node);
                if (node.isArray()) {
                    for (JsonNode contNode : node) {
                        Container c = this.createContainer(contNode);
                        // we have information about one transformation catalog c
                        mLogger.log("Container Entry parsed is - " + c,
                                     LogManager.DEBUG_MESSAGE_LEVEL);
                        store.addContainer(c);

                    }
                } else {
                    throw new ScannerException("transformations: value should be of type array ");
                }
            }
            //connect container references
            store.resolveContainerReferences();
        }
        return store;
    }

    /**
     * This method is used to extract the necessary information from the parsing
     * exception
     *
     * @param e The parsing exception generated from the yaml.
     *
     * @return String representing the line number and the problem is returned
     */
    private String parseError(JacksonYAMLParseException e) {
        StringBuilder builder = new StringBuilder();
        builder.append("Problem in the line :").append(e.getLocation().getLineNr()).
                append(", column:").append(e.getLocation().getColumnNr()).append(" ").
                append(e.getMessage());

        return builder.toString();
    }

    
    /**
     * Creates a transformation catalog entry object from a JSON node tree.
     * <pre>
 namespace: "example"
 name: "keg"
 version: "1.0"
 profiles:
     env:
         APP_HOME: "/tmp/myscratch"
         JAVA_HOME: "/opt/java/1.6"
     pegasus:
         clusters.num: "1"

 requires:
     - anotherTr

 sites:
   - name: "isi"
     type: "installed"
     pfn: "/path/to/keg"
     arch: "x86_64"
     os.type: "linux"
     os.release: "fc"
     os.version: "1.0"
     profiles:
       env:
           Hello: World
           JAVA_HOME: /bin/java.1.6
       condor:
           FOO: bar
     c: centos-pegasus
 </pre>
     *
     * @param node
     *
     * @return TransformationCatalogEntry
     */
    protected List<TransformationCatalogEntry> createTransformationCatalogEntry(JsonNode node) {
        List<TransformationCatalogEntry> entries = new LinkedList();
        TransformationCatalogEntry baseEntry = new TransformationCatalogEntry();
        if (node.has(TransformationCatalogKeywords.NAMESPACE.getReservedName())) {
            baseEntry.setLogicalNamespace(node.get(TransformationCatalogKeywords.NAMESPACE.getReservedName()).asText());
        }
        if (node.has(TransformationCatalogKeywords.NAME.getReservedName())) {
            baseEntry.setLogicalName(node.get(TransformationCatalogKeywords.NAME.getReservedName()).asText());
        }
        if (node.has(TransformationCatalogKeywords.VERSION.getReservedName())) {
            baseEntry.setLogicalVersion(node.get(TransformationCatalogKeywords.VERSION.getReservedName()).asText());
        }

        if (node.has(TransformationCatalogKeywords.PROFILES.getReservedName())) {
            baseEntry.addProfiles(createProfiles(node.get(TransformationCatalogKeywords.PROFILES.getReservedName())));
        }
        if (node.has(TransformationCatalogKeywords.REQUIRES.getReservedName())) {
            mLogger.log("Compound transformations are not yet supported. Specified in tx " + baseEntry.getLogicalName() ,
                        LogManager.ERROR_MESSAGE_LEVEL);
        }
        if (node.has(TransformationCatalogKeywords.SITES.getReservedName())) {
            TransformationCatalogEntry entry = (TransformationCatalogEntry) baseEntry.clone();
            JsonNode sitesNode = node.get(TransformationCatalogKeywords.SITES.getReservedName());
            if (sitesNode.isArray()) {
                for (JsonNode siteNode : sitesNode) {
                    addSiteInformation(entry, siteNode);
                    entries.add(entry);
                }
            } else {
                throw new ScannerException("sites: value should be of type array ");
            }
        }
        
        return entries;
    }

    /**
     * Creates a profile from a JSON node representing
     * <pre>
     * profiles:
     *  env:
     *      APP_HOME: "/tmp/myscratch"
     *      JAVA_HOME: "/opt/java/1.6"
     *  pegasus:
     *      clusters.num: "1"
     * </pre>
     *
     * @param node
     * @return Profiles
     */
    protected Profiles createProfiles(JsonNode node) {
        Profiles profiles = new Profiles();
        for (Iterator<Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
            Entry<String, JsonNode> entry = it.next();
            profiles.addProfilesDirectly(this.createProfiles(entry.getKey(), entry.getValue()));
        }
        return profiles;
    }

    /**
     * Creates a profile from a JSON node representing
     * <pre>
     * APP_HOME: "/tmp/myscratch"
     * JAVA_HOME: "/opt/java/1.6"
     * </pre>
     *
     * @param namespace
     * @param node
     * @return Profiles
     */
    protected List<Profile> createProfiles(String namespace, JsonNode node) {
        List<Profile> profiles = new LinkedList();
        if (Namespace.isNamespaceValid(namespace)) {
            for (Iterator<Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
                Entry<String, JsonNode> entry = it.next();
                profiles.add(new Profile(namespace, entry.getKey(), entry.getValue().asText()));
            }
        } else {
            throw new ScannerException("Invalid namespace specified " + namespace + " for profiles " + node);
        }
        return profiles;
    }
    
    /**
     * Parses site information from JsonNode and adds it to the transformation 
     * catalog entry. 
     * <pre>
     name: "isi"
     type: "installed"
     pfn: "/path/to/keg"
     arch: "x86_64"
     os.type: "linux"
     os.release: "fc"
     os.version: "1.0"
     profiles:
       env:
           Hello: World
           JAVA_HOME: /bin/java.1.6
       condor:
           FOO: bar
     c: centos-pegasus
 </pre>
     * @param entry
     * @param node 
     */
    protected void addSiteInformation(TransformationCatalogEntry entry, JsonNode node) {
        SysInfo sysInfo = new SysInfo();
        for (Iterator<Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
            Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            TransformationCatalogKeywords reservedKey = TransformationCatalogKeywords.getReservedKey(key);
            if (reservedKey == null) {
                throw new ScannerException(-1, "Illegal key " + key + " for sites: for transformation " + entry );
            }

            switch (reservedKey) {
                case NAME:
                    String siteName = node.get(key).asText();
                    entry.setResourceId(siteName);
                    break;

                case SITE_ARCHITECTURE:
                    String architecture = node.get(key).asText();
                    sysInfo.setArchitecture(SysInfo.Architecture.valueOf(architecture));
                    break;

                case SITE_OS:
                    String os = node.get(key).asText();
                    sysInfo.setOS(SysInfo.OS.valueOf(os));
                    break;

                case SITE_OS_RELEASE:
                    String release = node.get(key).asText();
                    sysInfo.setOSRelease(release);
                    break;

                case SITE_OS_VERSION:
                    Integer osVersion = (Integer) node.get(key).asInt();
                    sysInfo.setOSVersion(String.valueOf(osVersion));
                    break;

                case TYPE:
                    String type = node.get(key).asText();;
                    entry.setType(TCType.valueOf(type.toUpperCase()));
                    break;

                case PROFILES:
                    entry.addProfiles(this.createProfiles(node.get(TransformationCatalogKeywords.PROFILES.getReservedName())));
                    break;

                case METADATA:
                    throw new ScannerException("Metadata is unsupported currently");

                case SITE_PFN:
                    String pfn = node.get(key).asText();
                    entry.setPhysicalTransformation(pfn);
                    break;

                case SITE_CONTAINER_NAME:
                    String containerName = node.get(key).asText();
                    entry.setContainer(new Container(containerName));
                    break;

                default:
                    break;
            }
        }
        entry.setSysInfo(sysInfo);
    }
 
    protected Container createContainer(JsonNode node){
        Container c = new Container();
        for (Iterator<Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
            Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            TransformationCatalogKeywords reservedKey = TransformationCatalogKeywords.getReservedKey(key);
            if (reservedKey == null) {
                throw new ScannerException(-1, "Illegal key " + key + " for container " + node );
            }

            switch (reservedKey) {
                case NAME:
                    String containerName = node.get(key).asText();
                    c.setName(containerName);
                    break;

                case TYPE:
                    String type = node.get(key).asText();
                    c.setType(TYPE.valueOf(type));
                    break;

                case CONTAINER_IMAGE:
                    String url = node.get(key).asText();
                    c.setImageURL(url);
                    break;

                case CONTAINER_IMAGE_SITE:
                    String imageSite = node.get(key).asText();
                    c.setImageSite(imageSite);
                    break;

                case CONTAINER_DOCKERFILE:
                    String dockerFile = node.get(key).asText();
                    c.setImageDefinitionURL(dockerFile);
                    break;

                case CONTAINER_MOUNT:
                    String mountPoint = node.get(key).asText();
                    c.addMountPoint( mountPoint);
                    break;

                case PROFILES:
                    c.addProfiles(this.createProfiles(node.get(key)));
                    break;

                default:
                    break;
            }

        }
        return c;
    }

    
    /**
     * Remove potential leading and trailing quotes from a string.
     *
     * @param input is a string which may have leading and trailing quotes
     * @return a string that is either identical to the input, or a substring
     * thereof.
     */
    public String niceString(String input) {
        // sanity
        if (input == null) {
            return input;
        }
        int l = input.length();
        if (l < 2) {
            return input;
        }

        // check for leading/trailing quotes
        if (input.charAt(0) == '"' && input.charAt(l - 1) == '"') {
            return input.substring(1, l - 1);
        } else {
            return input;
        }
    }

    /**
     * Test function.
     *
     * @param args
     * @throws ProcessingException
     */
    public static void main(String[] args) throws ScannerException {
        try {
            Reader r = new VariableExpansionReader(
                    new FileReader(new File("/home/mukund/pegasus-5.0.0dev/bin/split_work/tc.yaml")));

            LogManager logger = LogManagerFactory.loadSingletonInstance();
            logger.setLevel(LogManager.DEBUG_MESSAGE_LEVEL);
            logger.logEventStart("event.pegasus.catalog.transformation.test", "planner.version", "2");

            TransformationCatalogYAMLParser p = new TransformationCatalogYAMLParser(r, new File(""), logger);
            p.parse(true);

        } catch (FileNotFoundException ex) {
            Logger.getLogger(TransformationCatalogYAMLParser.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ScannerException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

    }


}
