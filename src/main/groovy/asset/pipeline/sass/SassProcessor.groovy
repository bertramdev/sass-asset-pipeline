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
import com.bertramlabs.plugins.jruby.IsolatedScriptingContainer;
import asset.pipeline.AbstractProcessor
import asset.pipeline.AssetCompiler
import asset.pipeline.AssetFile
import asset.pipeline.AssetPipelineConfigHolder

@Log4j
class SassProcessor extends AbstractProcessor {
    public static final java.lang.ThreadLocal threadLocal = new ThreadLocal();
    public static final java.lang.ThreadLocal fileMap = new ThreadLocal();
    private static final $LOCK = new Object[0]
    static ScriptingContainer container
    // static StringWriter writer
    ClassLoader classLoader


    SassProcessor(AssetCompiler precompiler) {
        super(precompiler)
        try {
            synchronized($LOCK) {
                if(!SassProcessor.container) {
                    SassProcessor.container = new IsolatedScriptingContainer('sass');
                    //For compass compass: '1.0.1', chunky_png: '1.3.3', 'compass-core':'1.0.1'
                    def gemSet = [sass: '3.4.2',fssm: '0.2.10']
                    if(AssetPipelineConfigHolder.config?.sass?.gems) {
                        gemSet += AssetPipelineConfigHolder.config?.sass?.gems
                    }
                    SassProcessor.container.installGemDependencies(gemSet)
                    // if(!SassProcessor.writer) {
                    //     SassProcessor.writer = new StringWriter()    
                    // }
                    
                    // SassProcessor.container.setOutput(writer)
                    // SassProcessor.container.runScriptlet(buildInitializationScript())
                }
            }
        } catch (Exception e) {
            throw new Exception("SASS Engine initialization failed.", e)
        }
    }

    private String buildInitializationScript() {
        """
        if !defined?(Sass)
          require 'rubygems'
          require 'sass'
          require 'sass/plugin'
          require 'sass-asset-pipeline'
          require 'compass'
          require 'compass/sass_compiler'
          require 'compass-asset-pipeline'
        end

        Compass.configure_sass_plugin!
        """
    }


    //TODO: MAYBE FIX THIS LATER
    String process(String input, AssetFile assetFile) {
        def paths = []
        if(assetFile.parentPath) {
            paths << "/assets/${assetFile.parentPath}"

        }
        paths << "/assets"

        def args = ['--trace','-r','asset-file','-r','sass-asset-pipeline']

        if(AssetPipelineConfigHolder.config?.sass?.gems) {
            AssetPipelineConfigHolder.config?.sass?.gems.each { k,v ->
                args += ['-r', k]
            }
        }

        paths.each { path ->
            args += ['--load-path',path]
        }
        args << "/assets/${assetFile.canonicalPath}".toString()

        synchronized($LOCK) {
            def writer = new StringWriter()
            def pContainer = new IsolatedScriptingContainer('sass');

            pContainer.setOutput(writer)

            pContainer.runBinScript('sass',args as String[])
            def result = writer.toString()
            pContainer.remove("THIS_FILE")
            pContainer.terminate()
            return result
        }
    }

    //TODO: We may need this for compass later

    // String process(String input,AssetFile assetFile) {
        
    //     if(!this.precompiler) {
    //         threadLocal.set(assetFile);
    //     }
    //     fileMap.set([:])
    //     String assetRelativePath = assetFile.parentPath ?: ''
    //     def fileText
    //     def baseWorkDir = AssetPipelineConfigHolder.config?.sass?.workDir ?: '.sass-work'
    //     def workDir = new File(baseWorkDir, assetRelativePath)
    //     if(!workDir.exists()) {
    //         workDir.mkdirs()
    //     }
    //     // println "Working Directory ${workDir.canonicalPath}"
    //     container.put("to_path",workDir.canonicalPath)


    //     def paths = [assetRelativePath,'/']

    //     def pathstext = paths.collect{
    //         def p = it.replaceAll("\\\\", "/")
    //         if (p.endsWith("/")) {
    //             "${p}"
    //         } else {
    //             "${p}/"
    //         }
    //     }.join(",")

    //     def outputStyle = ":${AssetPipelineConfigHolder.config?.minifyCss ? 'compressed' : 'expanded'}"

    //     def additionalFiles = []
    //     container.put("assetFilePath", assetFile.path)
    //     container.put("load_paths", pathstext)
    //     container.put("project_path", new File('.').canonicalPath.replace(File.separator,AssetHelper.DIRECTIVE_FILE_SEPARATOR))
    //     container.put("working_path", assetFile.parentPath ? "/${assetFile.parentPath}".toString(): '')
    //     container.put("asset_path", assetFile.parentPath)
    //     container.put("precompiler_mode",precompiler ? true : false)
    //     container.put("additional_files", additionalFiles)
    //     def outputFileName = new File('target/assets',"${AssetHelper.fileNameWithoutExtensionFromArtefact(assetFile.name,assetFile)}.${assetFile.compiledExtension}".toString()).canonicalPath.replace(File.separator,AssetHelper.DIRECTIVE_FILE_SEPARATOR)
    //     try {
    //         container.put("file_dest", outputFileName)
    //         container.runScriptlet("""
    //             environment = precompiler_mode ? :production : :development

    //             Compass.add_configuration(
    //             {
    //             :cache => true,
    //             :project_path => working_path,
    //             :environment =>  environment,                
    //             #:generated_images_path => asset_path + '/images',
    //             :relative_assets => true,
    //             :sass_path => working_path,
    //             :css_path => working_path,
    //             :additional_import_paths => load_paths.split(','),
    //             :output_style => ${outputStyle}
    //             },
    //             'Grails' # A name for the configuration, can be anything you want
    //             )

    //             Compass.configuration.on_sprite_saved do |filename|
    //                 pathname = Pathname.new(filename)
    //                 additional_files << pathname.cleanpath.to_s
    //             end

    //         """)

    //         def configPath = assetFile.parentPath ? "${assetFile.parentPath}/config.rb".toString() : 'config.rb'
    //         def configFile =  AssetHelper.fileForFullName(configPath)
    //         if(configFile) {
    //             container.put('config_file', configPath)
    //         } else {
    //             container.put('config_file',null)
    //         }


    //         container.runScriptlet("""
    //             puts "Compiling file #{assetFilePath}"
    //             Compass.configure_sass_plugin!
    //             Compass.add_project_configuration AssetFile.new(config_file) if config_file
    //             compiler = Compass.sass_compiler({
    //               :only_sass_files => [
    //                 '/' + assetFilePath
    //               ]})
    //             compiler.compile!
    //             #Compass.sass_compiler.compile_if_required(assetFilePath, file_dest)
    //         """)

    //         // Lets check for generated files and add to precompiler
    //         // if(precompiler) {
    //         //     additionalFiles.each { filename ->
    //         //         def file = new File(filename)
    //         //         precompiler.filesToProcess << relativePath(file,true)
    //         //     }
    //         // }

    //         // def outputFile = new File(outputFileName)
    //         // println "Looking for ${outputFileName}"
    //         // if(outputFile.exists()) {
    //         //     println "Found File Contents ${fileText}"
    //         //     if(assetFile.encoding) {
    //         //         fileText = outputFile.getText(assetFile.encoding)
    //         //     } else {
    //         //         fileText = outputFile.getText()
    //         //     }
    //         // } else {
    //         //     println "Could not find Output File"
    //         //     fileText = input
    //         // }
    //         def map = fileMap.get()
    //         if(map.size() > 0) {
    //             map.each { k,v ->
    //                 println "Found Output File ${k}"
    //                 fileText = v
    //             }
    //         }
    //     } catch(e) {
    //         throw(e)
    //     } finally {
    //         def outputFile = new File(outputFileName)
    //         if(outputFile.exists()) {
    //             // outputFile.delete()
    //         }
    //     }

    //     return fileText
    // }



    static String onImport(String path) {
        def assetFile = threadLocal.get();
        def file = new File(path) //Returned from the Sass File Importer
        if(assetFile) {
          CacheManager.addCacheDependency(assetFile.file.canonicalPath, file)
        }

        return null
    }

    static void writeFile(String path, String content) {
        def myMap = fileMap.get()
        myMap[path] = content
    }

    static String convertStreamToString(InputStream istream) {
        return istream.text
    }
}
