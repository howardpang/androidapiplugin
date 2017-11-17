/*
 * Copyright 2015-present wequick.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.yy.android.gradle.javaapi

import com.yy.android.gradle.javaapi.util.DependenciesUtils
import org.gradle.api.Project
import org.gradle.api.Plugin
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileTree
import org.gradle.util.VersionNumber

public class ImportPlugin implements Plugin<Project> {

    protected Project project

    void apply(Project project) {
        this.project = project

        project.extensions.create('javaApiImport', com.yy.android.gradle.javaapi.ImportExtension.class)
        ImportExtension javaApiImport = project.javaApiImport
        project.afterEvaluate {
            boolean isApp = false
            if (android.class.name.find("com.android.build.gradle.AppExtension") != null) {
                isApp = true
            } else if (android.class.name.find("com.android.build.gradle.LibraryExtension") == null) {
                return
            }

            //println("9999999999999999 " + android.getBootClasspath()[0])

            def variants
            if (isApp) {
                variants = android.applicationVariants
            } else {
                variants = android.libraryVariants
            }

            variants.all { BaseVariant variant ->
                variant.mergeResources.finalizedBy project.task("SmallIport${variant.name}").doFirst {
                    Set<ResolvedDependency> compileResolveDepencies = DependenciesUtils.getAllResolveDependencies(project, "compile")
                    Map<File, File> replaceFiles = new HashMap<>()
                    Set<ResolvedDependency> exportDepencies = new HashSet<>()
                    Set<ResolvedDependency> otherDependencies = new HashSet<>()
                    if (javaApiImport.hookAutoImport) {
                        if (variant.name.capitalize() == "Release") {
                            javaApiImport.hookAutoImport = false
                        }
                    }
                    compileResolveDepencies.each {
                        collectIdeaLibraryFileInfo(it)
                        def version = it.moduleVersion
                        def name = it.moduleName
                        def group = it.moduleGroup
                        boolean isExportDependency = false
                        if (it.moduleArtifacts.size() > 0) {
                            it.moduleArtifacts.each { a ->
                                if (a.extension == "aar") {
                                    String classifier = a.classifier ? a.classifier : ""
                                    File aarDir = new File(project.buildDir, "intermediates/exploded-aar/${group}/${name}/${version}/${classifier}")
                                    File jarFile = new File(aarDir, "jars/classes.jar")
                                    File exportJarFile = new File(aarDir, "exportJar/classes.jar")
                                    if (exportJarFile.exists() && jarFile.exists()) {
                                        replaceFiles.put(jarFile, exportJarFile)
                                        exportDepencies.add(it)
                                        isExportDependency = true
                                    }
                                }
                            }
                        }
                        if (!isExportDependency) {
                            otherDependencies.add(it)
                        }
                    }

                    if (!replaceFiles.isEmpty()) {
                        variant.javaCompile.classpath -= project.files(replaceFiles.keySet())
                        variant.javaCompile.classpath += project.files(replaceFiles.values())
                    }

                    project.rootProject.gradle.buildFinished { result ->
                        if (exportDepencies.size() > 0 && javaApiImport.hookAutoImport) {
                            def ideaLibraryInfos = new HashSet()
                            exportDepencies.each {
                                ideaLibraryInfos.add(collectIdeaLibraryFileInfo(it))
                            }
                            boolean shouldCreateOtherIdeaLibraryFile = false

                            ideaLibraryInfos.each {
                                if (createIdeaLibraryFile(it)) {
                                    shouldCreateOtherIdeaLibraryFile = true
                                }
                            }
                            if (shouldCreateOtherIdeaLibraryFile) {
                                otherDependencies.addAll(collectOtherProjectDependencies())
                                ideaLibraryInfos.clear()
                                otherDependencies.each {
                                    ideaLibraryInfos.add(collectIdeaLibraryFileInfo(it))
                                }

                                ideaLibraryInfos.each {
                                    createIdeaLibraryFile(it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected com.android.build.gradle.BaseExtension getAndroid() {
        return project.android
    }

    private Set<ResolvedDependency> collectOtherProjectDependencies() {
        Set<Project> subprojects = project.rootProject.subprojects
        subprojects.remove(project)
        Set<ResolvedDependency> subprojectsDependencies = new HashSet<>()
        subprojects.each { p ->
            Set<ResolvedDependency> dependencies = DependenciesUtils.getAllResolveDependencies(p, "compile")
            subprojectsDependencies.addAll(dependencies)
        }
        return subprojectsDependencies
    }

    private String getNameWithoutExtension(File file) {
        String fileName = file.name
        int i = fileName.lastIndexOf(".")
        return i < 0 ? fileName : fileName.substring(0, i)
    }

    private static File findChildPath( File parentPath,  String childName) {
        for (File child : (parentPath.listFiles())) {
            if (childName == child.getName()) {
                return child.isFile() ? child : null
            }
        }
        return null
    }

    private File  findArtifactFilePath(File libraryPath, String fileNameSuffix) {
        if (!libraryPath.isFile()) {
            return null
        }
        File parentPath = libraryPath.getParentFile()
        String name = getNameWithoutExtension(libraryPath)
        String sourceFileName = name + fileNameSuffix
        if (parentPath != null) {

            // Try finding sources in the same folder as the jar file. This is the layout of Maven repositories.
            File sourceJar = findChildPath(parentPath, sourceFileName)
            if (sourceJar != null) {
                return sourceJar
            }

            // Try the parent's parent. This is the layout of the repository cache in .gradle folder.
            parentPath = parentPath.getParentFile()
            if (parentPath != null) {
                for (File child : (parentPath.listFiles())) {
                    if (child.isDirectory()) {
                        sourceJar = findChildPath(child, sourceFileName)
                        if (sourceJar != null) {
                            return sourceJar
                        }
                    }
                }
            }
        }
    }

    private def collectIdeaLibraryFileInfo(ResolvedDependency d) {
        String replaceJarInfo = null
        Set<String> jarsInfo = new HashSet<>()
        File docFile = null
        File sourceFile = null
        String moduleVersion = d.moduleVersion
        d.moduleArtifacts.each {
            if (it.type == "aar") {
                docFile = findArtifactFilePath(it.file, "-javadoc.jar")
                sourceFile = findArtifactFilePath(it.file, "-sources.jar")
                String classifier = it.classifier ? "${it.classifier}/" : ""
                File aarArtifactsDir = new File(project.buildDir, "intermediates/exploded-aar/${d.moduleGroup}/${d.moduleName}/${d.moduleVersion}/${classifier}")
                File explortJar = new File(aarArtifactsDir, "${ExportPlugin.EXPORT_JAR_DIR}/classes.jar")
                jarsInfo.add("file://\$PROJECT_DIR\$/" +  project.name + "/build/intermediates/exploded-aar/${d.moduleGroup}/${d.moduleName}/${d.moduleVersion}/${classifier}res")
                if (explortJar.exists()) {
                    jarsInfo.add("jar://\$PROJECT_DIR\$/" +  project.name + "/build/intermediates/exploded-aar/${d.moduleGroup}/${d.moduleName}/${d.moduleVersion}/${classifier}${ExportPlugin.EXPORT_JAR_DIR}/classes.jar!/")
                    replaceJarInfo = "jar://\$PROJECT_DIR\$/" +  project.name + "/build/intermediates/exploded-aar/${d.moduleGroup}/${d.moduleName}/${d.moduleVersion}/${classifier}jars/classes.jar!/"
                    FileTree jars = project.fileTree(new File(aarArtifactsDir.path, "jars")).include("**/*.jar").exclude("*.jar")
                    jars.each {
                        String subName = it.path.substring(project.rootDir.path.length(), it.path.length())
                        jarsInfo.add("jar://\$PROJECT_DIR\$" + subName.replace("\\", "/") + "!/")
                    }
                }else {
                    FileTree jars = project.fileTree(new File(aarArtifactsDir.path, "jars")).include("**/*.jar")
                    jars.each {
                        String subName = it.path.substring(project.rootDir.path.length(), it.path.length())
                        jarsInfo.add("jar://\$PROJECT_DIR\$" + subName.replace("\\", "/") + "!/")
                    }
                }
            }else if(it.type == "jar") {
                jarsInfo.add("jar://" +  it.file.path.replace("\\", "/") + "!/")
                docFile = findArtifactFilePath(it.file, "-javadoc.jar")
                sourceFile = findArtifactFilePath(it.file, "-sources.jar")
                moduleVersion = moduleVersion + "@jar"
            }
        }

        String docInfo
        String sourceInfo
        if (docFile != null) {
            docInfo = "jar://" + docFile.path.replace("\\", "/") + "!/"
        }
        if (sourceFile != null) {
            sourceInfo = "jar://" + sourceFile.path.replace("\\", "/") + "!/"
        }

        def info = [group: d.moduleGroup, name: d.moduleName, version:moduleVersion, replaceJar:replaceJarInfo, jars: jarsInfo ,doc:docInfo, source: sourceInfo]
        return info
    }

    private boolean createIdeaLibraryFile(def d) {
        VersionNumber gradleVersion = VersionNumber.parse(com.android.builder.Version.ANDROID_GRADLE_PLUGIN_VERSION)
        VersionNumber gradle231Version = new VersionNumber(2, 3, 1, "")
        File librariesDir = new File(project.rootDir, ".idea/libraries")

        String fileName = d.group + "_" + d.name + "_" + d.version
        fileName = fileName.replaceAll("[^a-zA-Z0-9]+", "_") + ".xml"
        File f = new File(librariesDir, fileName)
        boolean overwrite = true
        boolean shouldCreate = true
        if (f.exists()) {
            if (d.replaceJar != null) {
                // Should replace jar path
                def manifestParser = new XmlParser().parse(f)
                if (manifestParser.library.CLASSES.root.find { it.@url == d.replaceJar } == null) {
                    overwrite = false
                }
            } else {
                overwrite = false
            }
            shouldCreate = false
        }

        if (overwrite) {
            def pw = f.newPrintWriter()

            pw.println "<component name=\"libraryTable\">"
            pw.println("    <library name=\"${d.group}:${d.name}-${d.version}\"> ")
            pw.println("        <CLASSES>")
            d.jars.each {
                pw.println("            <root url=\"${it}\" />")
            }
            pw.println("        </CLASSES>")
            if (d.doc != null) {
                pw.println("        <JAVADOC>")
                pw.println("            <root url=\"${d.doc}\" />")
                pw.println("        </JAVADOC>")
            }else {
                pw.println("        <JAVADOC />")
            }
            if (d.source != null) {
                pw.println("        <SOURCES>")
                pw.println("            <root url=\"${d.source}\" />")
                pw.println("        </SOURCES>")
            }else {
                pw.println("        <SOURCES />")
            }
            pw.println("    </library>")
            pw.println("</component>")
            pw.flush()
            pw.close()
        }

        println("nnnnnnnnnnnnn " + d.group + ":" + d.name + ":" + d.version + " >> shouldCreate: " + shouldCreate + " overwrite: " + overwrite)
        return shouldCreate
    }

}
