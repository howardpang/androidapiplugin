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

import org.gradle.api.Project
import org.gradle.api.Plugin
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.file.FileTree
import org.gradle.api.artifacts.ArtifactCollection
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType

public class ImportPlugin implements Plugin<Project> {

    protected Project project

    void apply(Project project) {
        this.project = project

        project.extensions.create('javaApiImport', com.yy.android.gradle.javaapi.ImportExtension.class)
        ImportExtension javaApiImport = project.javaApiImport

        def exportDepencies = new HashSet<>()
        def otherDependencies = new HashSet<>()
        project.subprojects.each { subprj ->
            subprj.afterEvaluate {
                boolean isApp
                com.android.build.gradle.BaseExtension android = subprj.android
                if (android.class.name.find("com.android.build.gradle.AppExtension") != null) {
                    isApp = true
                } else if (android.class.name.find("com.android.build.gradle.LibraryExtension") != null) {
                    isApp = false
                }else {
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
                    subprj.tasks["process${variant.name.capitalize()}Manifest"].finalizedBy subprj.task("LibraryImport${variant.name.capitalize()}").doFirst {
                        if (javaApiImport.hookAutoImport) {
                            if (variant.name.capitalize() == "Release") {
                                javaApiImport.hookAutoImport = false
                            }
                        }
                        def compileDependencies = new HashSet()
                        collectCompileDependencies(variant, compileDependencies)
                        Map<File, File> replaceFiles = new HashMap<>()

                        compileDependencies.each {
                            boolean isExportDependency = false
                            if (it.exploded_aar != null) {
                                File jarFile = new File(it.exploded_aar, "jars/classes.jar")
                                File exportJarFile = new File(it.exploded_aar, "exportJar/classes.jar")
                                if (exportJarFile.exists() && jarFile.exists()) {
                                    replaceFiles.put(jarFile, exportJarFile)
                                    exportDepencies.add(it)
                                    isExportDependency = true
                                }
                            }
                            if (!isExportDependency) {
                                otherDependencies.add(it)
                            }
                        }

                        if (!replaceFiles.isEmpty()) {
                            variant.javaCompile.classpath -= subprj.files(replaceFiles.keySet())
                            variant.javaCompile.classpath += subprj.files(replaceFiles.values())
                        }
                    }
                }
            }
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

    private def collectCompileDependencies(BaseVariant variant, def compileDependencies) {
        if (variant == null) {
            return null
        }
        ArtifactCollection jars = variant.variantData.scope.getArtifactCollection(ConsumedConfigType.COMPILE_CLASSPATH, ArtifactScope.EXTERNAL, ArtifactType.JAR)
        ArtifactCollection aars = variant.variantData.scope.getArtifactCollection(ConsumedConfigType.COMPILE_CLASSPATH, ArtifactScope.EXTERNAL, ArtifactType.AAR)
        ArtifactCollection exploded_aars = variant.variantData.scope.getArtifactCollection(ConsumedConfigType.COMPILE_CLASSPATH, ArtifactScope.EXTERNAL, ArtifactType.EXPLODED_AAR)
        jars.artifacts.each {
            def splitResult = it.getId().componentIdentifier.displayName.split(":")
            def version = ""
            def name = ""
            def group = ""
            if (splitResult.length > 2) {
                version = splitResult[2]
                name = splitResult[1]
                group = splitResult[0]
            } else if (splitResult.length > 1) {
                name = splitResult[1]
                group = splitResult[0]
            } else if (splitResult.length > 0) {
                name = splitResult[0]
            }
            if (compileDependencies.find{ it.group == group && it.name == name && it.version == version } != null) {
                return
            }
            def aar = getArtifactFile(group, name, version, aars)
            def exploded_aar = getArtifactFile(group, name, version, exploded_aars)
            def info = [group: group, name: name, version: version, aar: aar, exploded_aar: exploded_aar, jar: it.file]
            compileDependencies.add(info)
        }
        return compileDependencies
    }

    private File getArtifactFile(String group, String name, String version, ArtifactCollection artifactCollection ) {
        def findResult = artifactCollection.artifacts.find {
            def splitResult = it.getId().componentIdentifier.displayName.split(":")
            def v = ""
            def n = ""
            def g = ""
            if (splitResult.length > 2) {
                v = splitResult[2]
                n = splitResult[1]
                g = splitResult[0]
            } else if (splitResult.length > 1) {
                n = splitResult[1]
                g = splitResult[0]
            } else if (splitResult.length > 0) {
                n = splitResult[0]
            }
            if (group == g && name == n && version == v) {
                return true
            }
        }
        return findResult == null ? null : findResult.file
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
        if ( libraryPath== null || !libraryPath.isFile()) {
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

    private def collectIdeaLibraryFileInfo(def d) {
        String replaceJarInfo = null
        Set<String> jarsInfo = new HashSet<>()
        File docFile = null
        File sourceFile = null
        String moduleVersion = d.version
        if (d.exploded_aar != null) {
            docFile = findArtifactFilePath(d.aar, "-javadoc.jar")
            sourceFile = findArtifactFilePath(d.aar, "-sources.jar")
            File aarArtifactsDir = d.exploded_aar
            File explortJar = new File(aarArtifactsDir, "${ExportPlugin.EXPORT_JAR_DIR}/classes.jar")
            jarsInfo.add("file://${aarArtifactsDir.path.replace("\\", "/")}/res")
            if (explortJar.exists()) {
                jarsInfo.add("jar://${aarArtifactsDir.path.replace("\\", "/")}/${ExportPlugin.EXPORT_JAR_DIR}/classes.jar!/")
                replaceJarInfo = ("jar://${aarArtifactsDir.path.replace("\\", "/")}/jars/classes.jar!/")
                FileTree jars = project.fileTree(new File(aarArtifactsDir.path, "jars")).include("**/*.jar").exclude("*.jar")
                jars.each {
                    jarsInfo.add("jar://${it.path.replace("\\", "/")}!/")
                }
            } else {
                FileTree jars = project.fileTree(new File(aarArtifactsDir.path, "jars")).include("**/*.jar")
                jars.each {
                    jarsInfo.add("jar://${it.path.replace("\\", "/")}!/")
                }
            }
        } else {
            jarsInfo.add("jar://" + d.jar.path.replace("\\", "/") + "!/")
            docFile = findArtifactFilePath(d.jar, "-javadoc.jar")
            sourceFile = findArtifactFilePath(d.jar, "-sources.jar")
            moduleVersion = moduleVersion + "@jar"
        }

        String docInfo
        String sourceInfo
        if (docFile != null) {
            docInfo = "jar://" + docFile.path.replace("\\", "/") + "!/"
        }
        if (sourceFile != null) {
            sourceInfo = "jar://" + sourceFile.path.replace("\\", "/") + "!/"
        }

        def info = [group: d.group, name: d.name, version: moduleVersion, replaceJar: replaceJarInfo, jars: jarsInfo, doc: docInfo, source: sourceInfo]
        return info
    }

    private boolean createIdeaLibraryFile(def d) {
        File librariesDir = new File(project.rootDir, ".idea/libraries")
        if (!librariesDir.exists()) librariesDir.mkdirs()

        String fileName = d.group + "_" + d.name + "_" + d.version
        fileName = fileName.replaceAll("[^a-zA-Z0-9]+", "_") + ".xml"
        File f = new File(librariesDir, fileName)
        boolean overwrite = true
        boolean shouldCreate = true
        if (f.exists()) {
            if (d.replaceJar != null) {
                // Should replace jar path
                def manifestParser = new XmlParser().parse(f)
                if (manifestParser.library.CLASSES.root.find { it.@url.indexOf(ExportPlugin.EXPORT_JAR_DIR) != -1 } != null) {
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
