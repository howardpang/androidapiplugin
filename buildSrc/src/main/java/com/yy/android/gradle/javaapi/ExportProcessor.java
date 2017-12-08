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
package com.yy.android.gradle.javaapi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import com.yy.android.gradle.javaapi.BuildConfig;

import static javax.tools.Diagnostic.Kind.WARNING;

public class ExportProcessor extends AbstractProcessor  {
    private Filer mFiler; //文件相关的辅助类
    private Elements mElementUtils; //元素相关的辅助类
    private Messager mMessager; //日志相关的辅助类
    private Set<Element> mHaveDumpElements;
    private List<File> mJavaSrcDirs;
    private File mExportClassDir;

    private static final String JAVA_SRC_DIRS_OPTION = "javaSrcDirs";
    private static final String EXPORT_CLASS_DIR_OPTION = "exportClassDir";
    //private static final String EXPORT_ANNOTATION_CLASS = "com.yy.android.annotation.Export";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        mFiler = processingEnv.getFiler();
        mElementUtils = processingEnv.getElementUtils();
        mMessager = processingEnv.getMessager();
        mHaveDumpElements = new HashSet<>();
        processingEnv.getMessager().printMessage(WARNING,  "init " );
        Iterator<Map.Entry<String, String>> iterator = processingEnv.getOptions().entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            processingEnv.getMessager().printMessage(WARNING,  "init >> " + entry.getKey() + ":" + entry.getValue());
            if (entry.getKey().equals(JAVA_SRC_DIRS_OPTION)) {
                String[] dirs = entry.getValue().split(";");
                mJavaSrcDirs = new ArrayList<>();
                for (String dir:dirs) {
                    File d = new File(dir);
                    if (d.exists()) {
                        mJavaSrcDirs.add(d);
                    }
                }
            }else if (entry.getKey().equals(EXPORT_CLASS_DIR_OPTION)) {
                mExportClassDir = new File(entry.getValue());
            }
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(BuildConfig.EXPORT_ANNOTATION_CLASS);
        return types;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_7;
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        try {
            if (mJavaSrcDirs == null || mJavaSrcDirs.isEmpty() || mExportClassDir == null) {
                return true;
            }
            //Class targetC = Class.forName("howard.myapplication.Export", true, roundEnvironment.getClass().getClassLoader());
            //Set<? extends Element> anUser = roundEnvironment.getElementsAnnotatedWith(targetC);
            //Set<? extends Element> anUser = roundEnvironment.getRootElements();
            //ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null).getLocation(StandardLocation.SOURCE_PATH);
            /*
            Filer filer = processingEnv.getFiler();
            //FileObject resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "tmp", (Element[]) null);
            FileObject resource = filer.getResource(StandardLocation.CLASS_OUTPUT, "howard.myapplication", "Hao.class");
            //FileObject jresource = filer.getResource(StandardLocation.SOURCE_PATH, "howard.myapplication", "Hao.java");
            Path resourcePath = Paths.get(resource.toUri());
            //Path jresourcePath = Paths.get(jresource.toUri());
            Path projectPath = resourcePath.getParent().getParent().getParent();
            //resource.delete();
            File exportInfoFile = new File(projectPath.toFile(), "export.txt");
            if (!exportInfoFile.exists()) {
                exportInfoFile.createNewFile();
            }
            */

            //processingEnv.getMessager().printMessage(WARNING,  "fffffffffff " +  resourcePath + ":" + resourcePath.toFile().exists() + " >> " + exportInfoFile.getAbsolutePath() + ":" + exportInfoFile.exists());

            //processingEnv.getMessager().printMessage(WARNING,  "jjjjjjjjjjjj " +  jresourcePath + ":" + jresourcePath.toFile().exists());

            for (Element element:set) {
                TypeElement typeElement = (TypeElement) element;
                if (typeElement != null) {
                    //processingEnv.getMessager().printMessage(WARNING,  typeElement.getQualifiedName() + " : " + typeElement.getSimpleName());
                    Set<? extends Element> anUser = roundEnvironment.getElementsAnnotatedWith(typeElement);
                    for (Element element2:anUser) {
                        TypeElement typeElement2 = (TypeElement) element2;
                        if (typeElement2 != null) {
                            dumpExportType(typeElement2);
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    String getTypeElementSrcJavaName(TypeElement typeElement) {
        String orgName = typeElement.getQualifiedName().toString().replace(".", "/");
        String foundName = orgName;
        boolean found = false;
        while (true) {
            for (File dir : mJavaSrcDirs) {
                File path = new File(dir, foundName + ".java");
                //processingEnv.getMessager().printMessage(WARNING, "getTypeElementSrcJavaName: " + path.getAbsolutePath());
                if (path.exists()) {
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
            // It may be inner class, so try to found its parent class
            int pos = foundName.lastIndexOf("/");
            if (pos == -1) {
                foundName = null;
                break;
            }
            foundName = foundName.substring(0, pos);
        }
        if (foundName == orgName) {
            foundName = foundName + ".class";
        }
        else if (foundName != null && foundName != orgName) {
            String subName = orgName.substring(foundName.length(), orgName.length());
            foundName = foundName + subName.replace("/", "$") + ".class";
        }
        return foundName;
    }


    void dumpExportType(TypeMirror typeMirror) {
        if (typeMirror == null) {
            return;
        }
        if (typeMirror instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) typeMirror;
            if (arrayType.getComponentType().getKind() == TypeKind.DECLARED) {
                dumpExportType(mElementUtils.getTypeElement(arrayType.getComponentType().toString()));
            }

        }else if (typeMirror instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            //processingEnv.getMessager().printMessage(WARNING, "getTypeArguments " + declaredType.getEnclosingType().toString());
            for (TypeMirror subTypeMirror : declaredType.getTypeArguments()) {
                //processingEnv.getMessager().printMessage(WARNING, "getTypeArguments " + typeMirror.toString());
                dumpExportType(mElementUtils.getTypeElement(subTypeMirror.toString()));
            }
            dumpExportType(mElementUtils.getTypeElement(declaredType.toString()));
        }
    }

    void dumpExportType(TypeElement typeElement) {
        if (typeElement == null || mHaveDumpElements.contains(typeElement)) {
            return;
        }
        //processingEnv.getMessager().printMessage(WARNING, "try dumpExportType: " + typeElement.getQualifiedName());
        mHaveDumpElements.add(typeElement);
        String srcJavaName = getTypeElementSrcJavaName(typeElement);
        if ( srcJavaName == null) {
            return;
        }

        if (typeElement != null && typeElement.getModifiers().contains(Modifier.PUBLIC)) {
            //processingEnv.getMessager().printMessage(WARNING, "dumpExportType: " + typeElement.getQualifiedName() + " >> " + srcJavaName);
            File outFile = new File(mExportClassDir, srcJavaName);
            if (!outFile.exists()) {
                if (!outFile.getParentFile().exists()) outFile.getParentFile().mkdirs();
                try {
                    boolean result = outFile.createNewFile();
                    //processingEnv.getMessager().printMessage(WARNING, "create class : "  + outFile.getPath() + " >> " + result);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //processingEnv.getMessager().printMessage(WARNING, "supperClass: " + typeElement.getSuperclass().toString());
            dumpExportType(typeElement.getSuperclass());
            /*
            for (TypeMirror inf:typeElement.getInterfaces()) {
                processingEnv.getMessager().printMessage(WARNING, "interface: " + inf.toString());
            }
            */
            for (Element element: typeElement.getEnclosedElements()) {
                Set<Modifier> modifiers = element.getModifiers();
                if(modifiers.contains(Modifier.PUBLIC)) {
                    //processingEnv.getMessager().printMessage(WARNING, "modify: " + element.getSimpleName() + " : " );
                    if (element instanceof VariableElement) {
                        VariableElement variableElement = (VariableElement) element;
                        //processingEnv.getMessager().printMessage(WARNING, "variableElement : " + variableElement.asType().toString() + " >> " + variableElement.asType().getKind());
                        dumpExportType(variableElement.asType());
                    } else if(element instanceof ExecutableElement) {
                        ExecutableElement executableElement = (ExecutableElement) element;
                        //processingEnv.getMessager().printMessage(WARNING, "method: return type " + executableElement.getSimpleName() + ":"+ executableElement.getReturnType());
                        dumpExportType(executableElement.getReturnType());
                        //processingEnv.getMessager().printMessage(WARNING, "method: receiver type " + executableElement.getSimpleName() + ":"+ executableElement.getReceiverType());
                        for (TypeParameterElement typeParameterElement: executableElement.getTypeParameters()) {
                            //processingEnv.getMessager().printMessage(WARNING, "method: typeParameters " + executableElement.getSimpleName() + ":"+ typeParameterElement);
                            dumpExportType(typeElement.asType());
                        }

                        for (VariableElement variableElement: executableElement.getParameters()) {
                            //processingEnv.getMessager().printMessage(WARNING, "method: parameters " + executableElement.getSimpleName() + ":"+ variableElement.asType() + " >> " + variableElement.asType().getKind());
                            dumpExportType(variableElement.asType());
                        }
                    } else if (element instanceof TypeElement) {
                        dumpExportType((TypeElement) element);
                    }
                }
            }
        }
    }
}
