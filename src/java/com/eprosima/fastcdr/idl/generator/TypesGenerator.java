package com.eprosima.fastcdr.idl.generator;

import com.eprosima.log.ColorMessage;
import com.eprosima.idl.generator.manager.TemplateManager;
import com.eprosima.idl.context.Context;
import com.eprosima.idl.parser.typecode.TypeCode;
import com.eprosima.idl.parser.tree.Definition;
import com.eprosima.idl.parser.tree.Export;
import com.eprosima.idl.parser.tree.Module;
import com.eprosima.idl.parser.tree.Interface;
import com.eprosima.idl.parser.tree.TypeDeclaration;

import org.antlr.stringtemplate.*;

import java.util.ArrayList;
import java.util.Map;
import java.io.*;

public class TypesGenerator
{
    public TypesGenerator(TemplateManager tmanager, String outputDir, boolean replace)
    {
        tmanager_ = tmanager;
        outputDir_ = outputDir;
        replace_ = replace;
        stg_ = tmanager_.createStringTemplateGroup("JavaType");
    }

    /*!
     * @brief This function generates data types in Java.
     * It uses a context that was processed by the IDL parser.
     */
    public boolean generate(Context context, String packagDir, String packag, String libraryName, Map<String, String> extensions)
    {
        ArrayList<Definition> definitions = context.getDefinitions();
        
        boolean returnedValue = processDefinitions(context, definitions, packagDir, packag, extensions);

        if(returnedValue)
        {
            // Create gradle build script.
            StringTemplateGroup gradlestg = tmanager_.createStringTemplateGroup("gradle");
            StringTemplate gradlest = gradlestg.getInstanceOf("main");
            gradlest.setAttribute("name", libraryName);

            if(!writeFile(outputDir_ + "build.gradle", gradlest))
            {
                System.out.println(ColorMessage.error() + "Cannot write file " + outputDir_ + "build.gradle");
                returnedValue = false;
            }
        }

        return returnedValue;
    }

    public boolean processDefinitions(Context context, ArrayList<Definition> definitions, String packagDir, String packag, Map<String, String> extensions)
    {
        if(definitions != null)
        {
            for(Definition definition : definitions)
            {
                if(definition.isIsModule())
                {
                    Module module = (Module)definition;

                    // Create directory for module.
                    String outputDir = packagDir  + module.getName();
                    File dir = new File(outputDir);

                    if(!dir.exists())
                    {
                        if(!dir.mkdir())
                        {
                            System.out.println(ColorMessage.error() + "Cannot create directory for module " + module.getName());
                            return false;
                        }
                    }
                    
                    if(!processDefinitions(context, module.getDefinitions(), outputDir + File.separator,
                            packag + "." + module.getName(), extensions))
                        return false;
                }
                else if(definition.isIsInterface())
                {
                    Interface ifc = (Interface)definition;

                    // Create StringTemplate of the interface
                    StringTemplate ifcst = stg_.getInstanceOf("interface");
                    ifcst.setAttribute("ctx", context);
                    ifcst.setAttribute("parent", ifc.getParent());
                    ifcst.setAttribute("interface", ifc);

                    StringTemplate extensionst = null;
                    String extensionname = null;
                    if(extensions != null && (extensionname = extensions.get("interface")) != null)
                    {
                        extensionst = stg_.getInstanceOf(extensionname);
                        extensionst.setAttribute("ctx", context);
                        extensionst.setAttribute("parent", ifc.getParent());
                        extensionst.setAttribute("interface", ifc);
                        ifcst.setAttribute("extension", extensionst.toString());
                    }
                    
                    if(processExports(context, ifc.getExports(), ifcst, extensions))
                    {
                        // Save file.
                        StringTemplate st = stg_.getInstanceOf("main");
                        st.setAttribute("ctx", context);
                        st.setAttribute("definitions", ifcst.toString());
                        st.setAttribute("package", (!packag.isEmpty() ? packag : null));

                        if(extensions != null && (extensionname = extensions.get("main")) != null)
                        {
                            extensionst = stg_.getInstanceOf(extensionname);
                            extensionst.setAttribute("ctx", context);
                            st.setAttribute("extension", extensionst.toString());
                        }

                        if(!writeFile(packagDir + ifc.getName() + ".java", st))
                        {
                            System.out.println(ColorMessage.error() + "Cannot write file " + packagDir + ifc.getName() + ".java");
                            return false;
                        }
                    }
                    else
                        return false;
                }
                else if(definition.isIsTypeDeclaration())
                {
                    TypeDeclaration typedecl = (TypeDeclaration)definition;

                    // get StringTemplate of the structure
                    StringTemplate typest = processTypeDeclaration(context, typedecl, extensions);

                    if(typest != null)
                    {
                        // Save file.
                        StringTemplate st = stg_.getInstanceOf("main");
                        st.setAttribute("ctx", context);
                        st.setAttribute("definitions", typest.toString());
                        st.setAttribute("package", (!packag.isEmpty() ? packag : null));

                        StringTemplate extensionst = null;
                        String extensionname = null;
                        if(extensions != null && (extensionname = extensions.get("main")) != null)
                        {
                            extensionst = stg_.getInstanceOf(extensionname);
                            extensionst.setAttribute("ctx", context);
                            st.setAttribute("extension", extensionst.toString());
                        }

                        if(!writeFile(packagDir + typedecl.getName() + ".java", st))
                        {
                            System.out.println(ColorMessage.error() + "Cannot write file " + packagDir + typedecl.getName() + ".java");
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    public boolean processExports(Context context, ArrayList<Export> exports, StringTemplate ifcst, Map<String, String> extensions)
    {
        for(Export export : exports)
        {
            if(export.isIsTypeDeclaration())
            {
                TypeDeclaration typedecl = (TypeDeclaration)export;

                // get StringTemplate of the structure
                StringTemplate typest = processTypeDeclaration(context, typedecl, extensions);

                if(typest != null)
                {
                    // Add type stringtemplate to interface stringtemplate.
                    ifcst.setAttribute("exports", typest.toString());
                }
            }
        }

        return true;
    }

    public StringTemplate processTypeDeclaration(Context context, TypeDeclaration typedecl, Map<String, String> extensions)
    {
        StringTemplate typest = null, extensionst = null;
        String extensionname = null;
        System.out.println("processTypesDeclaration " + typedecl.getName());

        if(typedecl.getTypeCode().getKind() == TypeCode.KIND_STRUCT)
        {
            typest = stg_.getInstanceOf("struct_type");
            typest.setAttribute("struct", typedecl.getTypeCode());

            // Get extension
            if(extensions != null && (extensionname =  extensions.get("struct_type")) != null)
            {
                extensionst = stg_.getInstanceOf(extensionname);
                extensionst.setAttribute("struct", typedecl.getTypeCode()); 
            }
        }
        else if(typedecl.getTypeCode().getKind() == TypeCode.KIND_UNION)
        {
            typest = stg_.getInstanceOf("union_type");
            typest.setAttribute("union", typedecl.getTypeCode());

            // Get extension
            if(extensions != null && (extensionname =  extensions.get("union_type")) != null)
            {
                extensionst = stg_.getInstanceOf(extensionname);
                extensionst.setAttribute("union", typedecl.getTypeCode()); 
            }
        }
        else if(typedecl.getTypeCode().getKind() == TypeCode.KIND_ENUM)
        {
            typest = stg_.getInstanceOf("enum_type");
            typest.setAttribute("enum", typedecl.getTypeCode());

            // Get extension
            if(extensions != null && (extensionname =  extensions.get("enum_type")) != null)
            {
                extensionst = stg_.getInstanceOf(extensionname);
                extensionst.setAttribute("enum", typedecl.getTypeCode()); 
            }
        }

        if(typest != null)
        {
            // Generate extension
            if(extensionst != null)
            {
                extensionst.setAttribute("ctx", context); 
                extensionst.setAttribute("parent", typedecl.getParent()); 
                typest.setAttribute("extension", extensionst.toString());
            }

            // Main stringtemplate
            typest.setAttribute("ctx", context);
            typest.setAttribute("parent", typedecl.getParent());
        }

        return typest;
    }

    private boolean writeFile(String file, StringTemplate template)
    {
        boolean returnedValue = false;
        
        try
        {
            File handle = new File(file);
            
            if(!handle.exists() || replace_)
            {
                FileWriter fw = new FileWriter(file);
                String data = template.toString();
                fw.write(data, 0, data.length());
                fw.close();
            }
            else
            {
                System.out.println("INFO: " + file + " exists. Skipping.");
            }

            returnedValue = true;
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }   

        return returnedValue;
    }

    private TemplateManager tmanager_ = null;
    private StringTemplateGroup stg_ = null;
    private String outputDir_ = null;
    private boolean replace_ = false;
}
