/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package asset.pipeline.sass

import asset.pipeline.AssetHelper

import groovy.util.logging.Log4j
import asset.pipeline.CacheManager
import org.jruby.embed.LocalVariableBehavior;
import org.jruby.embed.ScriptingContainer;
import org.jruby.RubyInstanceConfig.CompileMode;
import asset.pipeline.AbstractProcessor
import asset.pipeline.AssetCompiler
import asset.pipeline.AssetFile
import asset.pipeline.AssetPipelineConfigHolder

@Log4j
class SassProcessor extends AbstractProcessor {
    public static final java.lang.ThreadLocal threadLocal = new ThreadLocal();
    private static final $LOCK = new Object[0]
    static ScriptingContainer container
    ClassLoader classLoader


    SassProcessor(AssetCompiler precompiler) {
        super(precompiler)
        try {
            synchronized($LOCK) {
                if(!SassProcessor.container) {
                    SassProcessor.container = new ScriptingContainer(LocalVariableBehavior.PERSISTENT);
                    SassProcessor.container.setEnvironment([:])    
                    SassProcessor.container.runScriptlet(buildInitializationScript())
                }
            }
        } catch (Exception e) {
            throw new Exception("SASS Engine initialization failed.", e)
        }
    }

    private String buildInitializationScript() {
        """
        if !defined?(Sass)
          puts 'Loading Sass... we hope.'
          require 'rubygems'
          require 'sass'
          require 'sass/plugin'
          require 'sass-asset-pipeline'
          require 'compass'
          require 'compass-asset-pipeline'
        end
        
        frameworks = Dir.new(Compass::Frameworks::DEFAULT_FRAMEWORKS_PATH).path
        Compass::Frameworks.register_directory(File.join(frameworks,'compass'))
        Compass::Frameworks.register_directory(File.join(frameworks,'blueprint'))
        Compass.configure_sass_plugin!
        """
    }


    String process(String input,AssetFile assetFile) {
        
        if(!this.precompiler) {
            threadLocal.set(assetFile);
        }
        String assetRelativePath = assetFile.parentPath ?: ''
        def fileText
        def workDir = new File("target/assets", assetRelativePath)
        if(!workDir.exists()) {
            workDir.mkdirs()
        }
        println "Working Directory ${workDir.canonicalPath}"
        container.put("to_path",workDir.canonicalPath)


        def paths = [assetRelativePath,'/']

        def pathstext = paths.collect{
            def p = it.replaceAll("\\\\", "/")
            if (p.endsWith("/")) {
                "${p}"
            } else {
                "${p}/"
            }
        }.join(",")

        def outputStyle = ":${AssetPipelineConfigHolder.config?.minifyCss ? 'compressed' : 'expanded'}"

        def additionalFiles = []
        container.put("assetFilePath", assetFile.path)
        container.put("load_paths", pathstext)
        container.put("project_path", new File('.').canonicalPath.replace(File.separator,AssetHelper.DIRECTIVE_FILE_SEPARATOR))
        container.put("working_path", '/')
        container.put("asset_path", assetFile.parentPath)
        container.put("precompiler_mode",precompiler ? true : false)
        container.put("additional_files", additionalFiles)
        def outputFileName = new File('target/assets',"${AssetHelper.fileNameWithoutExtensionFromArtefact(assetFile.name,assetFile)}.${assetFile.compiledExtension}".toString()).canonicalPath.replace(File.separator,AssetHelper.DIRECTIVE_FILE_SEPARATOR)
        try {
            container.put("file_dest", outputFileName)
            container.runScriptlet("""
                environment = precompiler_mode ? :production : :development

                Compass.add_configuration(
                {
                :cache => true,
                :project_path => working_path,
                :environment =>  environment,                
                #:generated_images_path => asset_path + '/images',
                :relative_assets => true,
                :sass_path => working_path,
                :css_path => working_path,
                :additional_import_paths => load_paths.split(','),
                :output_style => ${outputStyle}
                },
                'Grails' # A name for the configuration, can be anything you want
                )

                Compass.configuration.on_sprite_saved do |filename|
                    pathname = Pathname.new(filename)
                    additional_files << pathname.cleanpath.to_s
                end

            """)

            // def configFile = new File(assetFile.file.getParent(), "config.rb")
            // if(configFile.exists()) {
            //     container.put('config_file',configFile.canonicalPath.replace(File.separator,AssetHelper.DIRECTIVE_FILE_SEPARATOR))
            // } else {
                container.put('config_file',null)
            // }


            container.runScriptlet("""
                puts "Compiling file #{assetFilePath}"
                Compass.configure_sass_plugin!
                Compass.add_project_configuration config_file if config_file
                Compass.compiler.compile_if_required(assetFilePath, file_dest)
            """)

            // Lets check for generated files and add to precompiler
            if(precompiler) {
                additionalFiles.each { filename ->
                    def file = new File(filename)
                    precompiler.filesToProcess << relativePath(file,true)
                }
            }

            def outputFile = new File(outputFileName)
            println "Looking for ${outputFileName}"
            if(outputFile.exists()) {
                println "Found File Contents ${fileText}"
                if(assetFile.encoding) {
                    fileText = outputFile.getText(assetFile.encoding)
                } else {
                    fileText = outputFile.getText()
                }
            } else {
                println "Could not find Output File"
                fileText = input
            }
        } catch(e) {
            throw(e)
        } finally {
            def outputFile = new File(outputFileName)
            if(outputFile.exists()) {
                // outputFile.delete()
            }
        }

        return fileText
    }



    static String onImport(String path) {
        def assetFile = threadLocal.get();
        def file = new File(path) //Returned from the Sass File Importer
        if(assetFile) {
          CacheManager.addCacheDependency(assetFile.file.canonicalPath, file)
        }

        return null
    }

    static String convertStreamToString(InputStream istream) {
        return istream.text
    }
}
