/**
 * Created by aprattes on 22.07.14.
 **/

import com.sun.org.apache.xalan.internal.xsltc.compiler.util.Type

import static groovy.io.FileType.FILES
import groovy.lang.Binding

/*
//for local testpurposes
String rootpom_path = "/tmp/product/product/pom.xml"
String dryrun = "false"
String regex = "(.*tests.jar.*|.*sources.jar.*)"
String whereToDownload = "/tmp/dwnld"

String old_repo_url = "http://grzmvn01.infonova.at:8082/nexus/content/repositories/product-releases/"
String username = "aprattes"
String password = "aP2206Live1"

String new_repo_url = "http://grzmvn02:8081/nexus/content/repositories/local-product-releases/"
String username2 = "aprattes"
String password2 = ""

String download = "true"
String upload = "false"
*/

boolean bDownload = Boolean.parseBoolean(download)
boolean bUpload = Boolean.parseBoolean(upload)

//lets go...
if (!rootpom_path.endsWith("pom.xml")) {
    throw new Exception("rootpom-file doesn't end with 'pom.xml'! " + rootpom_path)

}

println "[INFO]"
println "[INFO] =========================================================="
println "[INFO]      Copy Artifacts from one Nexus to another Tool"
println "[INFO] =========================================================="
println "[INFO] Parameters:"
println "[INFO]   - source      : " + old_repo_url
println "[INFO]   - dest.       : " + new_repo_url
println "[INFO]   - user        : " + username
println "[INFO]   - dryrun      : " + dryrun
println "[INFO]   - rootpom     : " + rootpom_path
println "[INFO]   - download to : " + whereToDownload
println "[INFO]   - regex       : " + regex
println "[INFO]   - download    : " + bDownload
println "[INFO]   - upload      : " + bUpload
println "[INFO]"


def pomArr = []
File rootPomFile = new File(rootpom_path)
File f = rootPomFile.getParentFile()

//find pom.xml files
f.eachFileRecurse(FILES) {
    if (it.name.equals('pom.xml')) {

        if (!it.toString().contains("/src/main/resources") && !it.toString().contains("/src/test/resources")) {
            pomArr.add(new POM(it))
        }
    }
}

//find parents
pomArr.each { pom1 ->
    pomArr.each { pom2 ->
        if (pom2.strParent.equals(pom1.toString())) {
            pom2.parent = pom1
            pom1.children.add(pom2)
        }
    }
}

//find submodules of Poms
pomArr.each { pom1 ->
    pomArr.each { pom2 ->
        pom1.strSubmodules.each { submodule ->
            //a pom is a submodule, when it's parent contains it in the modules
            if (submodule.equals(pom2.pomFile.getParentFile().name)) { //&& isInRelationShip(pom1, pom2)) { //parent fix
                if (!pom1.pomSubmodules.contains(pom2)) {
                    pom1.pomSubmodules.add(pom2)
                }
            }
        }
    }
}

//a little Errorcheck

pomArr.each {
    if (it.version == null || it.version.isEmpty()) {
        throw new Exception(String.format("%s has no version!", it.pomFile.toString))
    }

    if (it.groupId == null || it.groupId.isEmpty()) {
        throw new Exception(String.format("%s has no groupId!", it.pomFile.toString))
    }
}

//find rootPOM
pomArr.each {
    if (rootPomFile.getAbsolutePath().equals(it.pomFile.getAbsolutePath())) {

        println "[INFO]"
        println "[INFO] === Module Dependency Tree ==="
        println "[INFO]"
        //print relationship tree
        printPomAndSubmodules(it, 0);
        println "[INFO]"
        println "[INFO] === Downloading Artifacts ==="
        println "[INFO]"

        //download Artifacts
        boolean dry = ("" + dryrun).toBoolean()

        if(bDownload) {
            if(!it.toString().matches(regex)) {
                createwgetScriptandRunIt(it, old_repo_url, username, password, dry, whereToDownload)
            }
        }
        else {
            println "[INFO] SKIPPING.. download parameter is 'false'"
        }

        if (!dry) {


            println "[INFO]"
            println "[INFO] === Uploading Artifacts ==="
            println "[INFO]"

            if(bUpload) {
                //upload Artifacts
                def cmdArr = getUploadCommands(whereToDownload, regex, old_repo_url, new_repo_url).split("\n")

                cmdArr.each { command ->
                    println "[INFO] execute: " + command
                    command = command.replace("\$password", password2).replace("\$username", username2)
                    uploadToArtifactory(command)
                }
            }
            else
            {
                println "[INFO] SKIPPING... upload Parameter is 'false'!"
            }
        }

    }
}

private uploadToArtifactory(command) {
    executeCommand(command, false, true)
}


return "SUCCESS!"

//###### methods and classes ######
/***
 * Creates the curl script for deleting things from nexus and runs it, if this is no dry-run
 */
def createwgetScriptandRunIt(
        def rootPom, String repoUrl, String username, String password, boolean dryrun, String downloadpath) {
    def scripts = null;
    boolean error = false;
    script = getScriptHelper(rootPom, repoUrl, downloadpath)

    if (dryrun) {
        println "[INFO] THIS IS A DRY-RUN! nothing will happen"
    }

    script.each { String cmd ->

        println "[INFO] execute " + cmd
        cmd = cmd.replace("\$password", password).replace("\$username", username)
        if (!dryrun) {
            cmd = cmd.replace("\$password", password).replace("\$username", username)

            try {
                downloadFromNexus(cmd)
            }
            catch (Exception e) {
                println("[ERROR] couldn't download artifact with: " + cmd.replace(password, "*******"))
                println("[ERROR] Message: " + e.getMessage())
                error = true
            }
        }
    }

    if (error) {
        throw new Exception("[ERROR] while downloading artifacts.\n[ERROR] read logfile!!")
    }

}

private downloadFromNexus(String cmd) {
    executeCommand(cmd, true, false)
}

def sayhallo(File downloadPath, String regex, String old_repo_URL, String newNexusRepo, String user, String password)
{
    println "OK, Hallo"
}
/***
 * recursive help for create scripts
 *
 * @param pom
 * @param repoUrl
 * @param username
 * @param password
 * @return
 */
def List getScriptHelper(def pom, String repoUrl, String downloadpath) {
    List cmdList = new ArrayList()

    if (pom.pomSubmodules != null) {
        pom.pomSubmodules.each {
            cmdList.addAll(getScriptHelper(it, repoUrl, downloadpath))
        }
    }

    cmdList.add(String.format("wget  -r -l 1 -P %s --user \$username --password \$password %s/%s/%s/%s", downloadpath,
            repoUrl, pom.groupId.replace(".", "/"), pom.artifactId, pom.version))


    //remove duplicates if any
    Set setItems = new LinkedHashSet(cmdList);
    cmdList.clear();
    cmdList.addAll(setItems);

    return cmdList
}


def String getUploadCommands(String downloadloc, String notAllowedRegex, String oldRepo, String newRepo) {

    File downloadFolder = new File(downloadloc)

    println "[INFO] creating Cmds.."
    String cmds = ""
    String standartRegEx = ".*(md5|sha1|index.html|/nexus/favicon.png|/static/css/Sonatype-content.*)";

    downloadFolder.eachFileRecurse(FILES) { file ->
        //curl --upload-file my.zip -u admin:admin123 -v http:\/\/192.168.1.50:8081\/nexus\/service\/local\/repositories\/releases\/content-compressed\/foo\/bar
        //cmds += "curl --upload-file my.zip -u admin:admin123 -v http://192.168.1.50:8081/nexus/service/local/repositories/releases/content-compressed/foo/bar"

        if (!file.getAbsolutePath().matches(standartRegEx)) {
            if (!file.getAbsolutePath().matches(notAllowedRegex)) {
                String replacer = oldRepo.replace("http://", "").replace("https://", "").replace("//", "/")
                replacer = downloadFolder.getAbsolutePath() + "/" + replacer
                String uploadUrl = ""

                if (newRepo.endsWith("/")) {
                    uploadUrl = String.format("%s%s", newRepo, file.getAbsolutePath().replace(replacer, ""))
                } else {
                    uploadUrl = String.format("%s/%s", newRepo, file.getAbsolutePath().replace(replacer, ""))
                }

                cmds += String.format("curl -u \$username:\$password --upload-file %s -v %s\n", file.getAbsolutePath(), uploadUrl)
            } else {
                println "[INFO] Ignore " + file.getAbsolutePath() + " due to regex"
            }
        }
    }

    return cmds;
}

//POM functions
/***
 * Checking for far relationships..
 * @param pom1
 * @param pom2
 * @return
 */
def boolean isInRelationShip(def pom1, def pom2) {
    if (pom2 != null) {
        if (pom1.toString().equals(pom2.parent.toString())) {
            return true
        } else {
            return isInRelationShip(pom1, pom2.parent)
        }
    }
    return false
}

def void printPomAndSubmodules(def pom, def deep) {
    print "[INFO]"
    for (int i = 0; i < deep; i++) {
        print "\t"
    }

    if (pom.pomSubmodules.size > 0) {
        print "+ "
    } else {
        print "\\ "
    }

    println String.format("%s:%s:%s", pom.groupId, pom.artifactId, pom.version)

    pom.pomSubmodules.each {
        printPomAndSubmodules(it, deep + 1)
    }
}

def executeCommand(def cmd, boolean nexus, boolean upload) {
    def stdout = new StringBuffer()
    def stderr = new StringBuffer()

    def proc = "${cmd}".execute()
    proc.consumeProcessOutput(stdout, stderr)
    proc.waitFor()

    println "[INFO] stdout:\n${stdout}"
    println "[INFO] stderr:\n${stderr}"

    def newout ="${stderr}".split("\n")

    println "[INFO] out: " + newout.last()

    if(nexus) {
        if (!stdout.empty) {
            throw new Exception("An Error occured while donwloading a Artifact, read stdout!")
        }
    } else {
        if (upload && !stdout.contains("201 Created")) {
            throw new Exception("An Error occured while donwloading a Artifact, read stdout!")
        }
    }


    if (proc.exitValue()) {
        println "[ERROR] Command '${cmd}' exited with ${proc.exitValue()}"
        throw new Exception("[ERROR] Command '${cmd}' exited with ${proc.exitValue()}")
    }
}

class POM {
    String groupId
    String artifactId
    String version
    String strParent

    POM parent
    def strSubmodules = []
    def pomSubmodules = []
    def modules = []
    def children = []

    File pomFile


    POM(File pomFile) {
        this.pomFile = pomFile
        readPOM()
    }

    private void readPOM() {
        XmlParser parser = new XmlParser()
        def project = parser.parse(pomFile)

        groupId = project.groupId.text()
        artifactId = project.artifactId.text()
        version = project.version.text()

        if (project.modules != null) {
            project.modules.module.each {
                strSubmodules.add(it.text())
            }
        }

        //Bugfix for Albert
        if (project.profiles != null) {
            project.profiles.profile.each { prof ->
                if (prof.modules != null) {
                    prof.modules.module.each {
                        strSubmodules.add(it.text())
                    }
                }
            }
        }

        if (version == null || version.isEmpty()) {
            version = project.parent.version.text()
        }

        if (groupId == null || groupId.isEmpty()) {
            groupId = project.parent.groupId.text()
        }

        strParent = String.format("%s:%s:%s", project.parent.groupId.text(), project.parent.artifactId.text(), project.parent.version.text())
    }

    @Override
    boolean equals(Object obj) {
        if (obj instanceof POM) {
            POM other = (POM) obj
            //println other.toString() + " " + this.toString()
            return (this.artifactId.equals(other.artifactId) && this.version.equals(other.version) && this.groupId.equals(other.groupId))
        }

        retrun false
    }

    @Override
    String toString() {
        return String.format("%s:%s:%s", this.groupId, this.artifactId, this.version)
    }
}