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
package com.yy.android.gradle.javaapi.util

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency

import java.security.DigestInputStream
import java.security.MessageDigest

/** Class to resolve project dependencies */
public final class DependenciesUtils {

    public static Set<ResolvedDependency> getAllResolveDependencies(Project project, String config) {
        Configuration configuration
        try {
            configuration = project.configurations[config]
        } catch (UnknownConfigurationException ignored) {
            return null
        }

        return getAllResolveDependencies(configuration)
    }

    public static Set<ResolvedDependency> getFirstLevelDependencies(Project project, String config) {
        def configuration = project.configurations[config]
        ResolvedConfiguration resolvedConfiguration = configuration.resolvedConfiguration
        def firstLevelDependencies = resolvedConfiguration.firstLevelModuleDependencies
        return firstLevelDependencies
    }

    public static void collectAllDependencies(Project prj, Set<Dependency> allDependencies, String config ) {
        //Defining configuration names from which dependencies will be taken (debugCompile or releaseCompile and compile)
        prj.configurations[config].allDependencies.each { depend ->
            if (allDependencies.find { addedNode -> addedNode.group == depend.group && addedNode.name == depend.name } == null) {
                allDependencies.add(depend)
            }
            if (depend instanceof DefaultProjectDependency) {
                collectAllDependencies(depend.dependencyProject, allDependencies, config)
            }
        }
    }

    public static Set<ResolvedDependency> getAllResolveDependencies(Configuration configuration) {
        ResolvedConfiguration resolvedConfiguration = configuration.resolvedConfiguration
        def firstLevelDependencies = resolvedConfiguration.firstLevelModuleDependencies
        Set<ResolvedDependency> allDependencies = new HashSet<>()
        firstLevelDependencies.each {
            collectDependencies(it, allDependencies)
        }
        return allDependencies
    }

    private static void collectDependencies(ResolvedDependency node, Set<ResolvedDependency> out) {
        if (out.find { addedNode -> addedNode.name == node.name } == null) {
            out.add(node)
        }
        // Recursively
        node.children.each { newNode ->
            collectDependencies(newNode, out)
        }
    }

    public static void collectProjectDependencies(Project prj, Set<DefaultProjectDependency> allDependencies ) {
        //Defining configuration names from which dependencies will be taken (debugCompile or releaseCompile and compile)
        prj.configurations['compile'].dependencies.withType(DefaultProjectDependency.class).each { depend ->
            if (allDependencies.find { addedNode -> addedNode.group == depend.group && addedNode.name == depend.name } == null) {
                allDependencies.add(depend)
                collectProjectDependencies(depend.dependencyProject, allDependencies)
            }
        }
    }

    public static String generateMD5(File file) {
        file.withInputStream {
            new DigestInputStream(it, MessageDigest.getInstance('MD5')).withStream {
                it.eachByte {}
                it.messageDigest.digest().encodeHex() as String
            }
        }
    }
}