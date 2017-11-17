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
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import com.yy.android.gradle.javaapi.BuildConfig

public class ExportPlugin implements Plugin<Project> {

    private static final String EXPORT_JAR_DIR = "exportJar"
    //private static final String GROUP = "com.yy.android.gradle.build.tool"
    //private static final String NAME = "gradle-javaapi"
    //private static final String EXPORT_ANNOTATION_MODULE_GROUP = "com.yy.android.annotation"
    //private static final String EXPORT_ANNOTATION_MODULE_NAME  = "export"
    //private static final String EXPORT_ANNOTATION_PROCESSOR_CLASS = "com.yy.android.gradle.javaapi.ExportProcessor"

    protected Project project

    void apply(Project project) {
        this.project = project
        String thisJarPath = "${project.rootDir}/buildSrc/build/libs/gradle-javaapi.jar"
        def configuration = project.rootProject.buildscript.configurations.classpath
        String thisVersion
        ResolvedConfiguration resolvedConfiguration = configuration.resolvedConfiguration
        resolvedConfiguration.firstLevelModuleDependencies.each { ResolvedDependency d ->
            if (d.moduleGroup == BuildConfig.GROUP && d.moduleName == BuildConfig.NAME) {
                thisVersion = d.moduleVersion
                d.moduleArtifacts.each {
                    thisJarPath = it.file.path
                }
            }
        }

        if (!new File(thisJarPath).exists()) {
            throw new Exception("Can't find plugin jar in path : ${thisJarPath}")
        }

        if (project.rootProject.subprojects.find{ it.name == "export_annotation"} == null) {
            //!! Note, we auto add 'Export' annotation to project, so user no need add it manually
            project.dependencies.add("compile", "${BuildConfig.EXPORT_ANNOTATION_MODULE_GROUP}:${BuildConfig.EXPORT_ANNOTATION_MODULE_NAME}:${thisVersion}")
        }

        project.afterEvaluate {
            if (android.class.name.find("com.android.build.gradle.LibraryExtension") == null) {
                //return
            }
            boolean isApp = false
            if (android.class.name.find("com.android.build.gradle.AppExtension") != null) {
                isApp = true
            } else if (android.class.name.find("com.android.build.gradle.LibraryExtension") == null) {
                return
            }

            def variants
            if (isApp) {
                variants = android.applicationVariants
            } else {
                variants = android.libraryVariants
            }

            variants.all { BaseVariant variant ->
                List<File> javaSrcDirs = new ArrayList<>()
                variant.sourceSets.each {
                    javaSrcDirs.addAll(it.javaDirectories)
                }

                Zip bundle = project.tasks["bundle${variant.name.capitalize()}"]
                File exportClassDir = new File(project.buildDir, "intermediates/exportClasses/${variant.name}")
                variant.javaCompile.options.compilerArgs += [
                        '-processorpath', thisJarPath,
                        '-processor', BuildConfig.EXPORT_ANNOTATION_PROCESSOR_CLASS,
                        "-A${ExportProcessor.JAVA_SRC_DIRS_OPTION}=${javaSrcDirs.join(";")}",
                        "-A${ExportProcessor.EXPORT_CLASS_DIR_OPTION}=${exportClassDir.path}"
                ]
                variant.javaCompile.doLast { JavaCompile jc ->
                    File srcClassDir = jc.destinationDir
                    String exportClassDirPath = exportClassDir.path
                    project.fileTree(exportClassDir).each { dstFile ->
                        String className = dstFile.path.substring(exportClassDirPath.length(), dstFile.path.length())
                        File srcFile = new File(srcClassDir, className)
                        project.copy {
                            from srcFile
                            into dstFile.parentFile
                        }
                    }
                }

                bundle.doFirst {
                    File exportJar = new File(project.buildDir, "intermediates/bundles/${variant.name}/${EXPORT_JAR_DIR}/classes.jar")
                    if (!exportJar.parentFile.exists()) exportJar.parentFile.mkdirs()
                    if (exportClassDir.exists()) {
                        project.ant.zip(destfile: exportJar.path, basedir: exportClassDir.path)
                    }
                    File exprotFile = new File(exportJar.parentFile, "export_classes.txt")
                    def pw = exprotFile.newPrintWriter()
                    project.fileTree(exportClassDir).each { dstFile ->
                        String className = dstFile.path.substring(exportClassDir.path.length(), dstFile.path.length() - 6)
                        pw.println(className.replace("\\", "/"))
                    }
                    pw.flush()
                    pw.close()
                }
            }
        }
    }

    protected com.android.build.gradle.BaseExtension getAndroid() {
        return project.android
    }

}
