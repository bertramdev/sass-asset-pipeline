require 'sass' #We want to make sure sass is already loaded before we start monkey patching
require 'java'

# Let the Monkey Patching Commence! (This is compass' fault)

Sass::Importers::Filesystem.class_eval do
	def find(name, options)
		result = _find(@root, name, options)
		if result
			Java::AssetPipelineSass::SassProcessor.onImport(result.options[:filename])
		end
		return result
	end

	def find_relative(name, base, options)
		result = _find(File.dirname(base), name, options)
		if result
			Java::AssetPipelineSass::SassProcessor.onImport(result.options[:filename])
		end
		return result
	end

	def mtime(uri, options)
		return nil
	end

	def possible_asset_files(name)
        name = escape_glob_characters(name)
        dirname, basename, extname = split(name)
        sorted_exts = extensions.sort
        syntax = extensions[extname]

        if syntax
          ret = [["#{dirname}/#{basename}.#{extensions.invert[syntax]}", syntax],["_#{dirname}/#{basename}.#{extensions.invert[syntax]}", syntax]]
        else
          ret = sorted_exts.map {|ext, syn| [["#{dirname}/#{basename}.#{ext}", syn],["_#{dirname}/#{basename}.#{ext}", syn]]}
          ret = Sass::Util.flatten(ret,1)
        end
        # JRuby chokes when trying to import files from JARs when the path starts with './'.
        ret.map {|f, s| [f.sub(%r{^\./}, ''), s]}
    end

	# Given a base directory and an `@import`ed name,
    # finds an existant file that matches the name.
    #
    # @param dir [String] The directory relative to which to search.
    # @param name [String] The filename to search for.
    # @return [(String, Symbol)] A filename-syntax pair.
	def find_asset_file(dir, name, options)
        # on windows 'dir' can be in native File::ALT_SEPARATOR form
        dir = dir.gsub(File::ALT_SEPARATOR, File::SEPARATOR) unless File::ALT_SEPARATOR.nil?

        found = possible_asset_files(remove_root(name)).map do |f, s|
          path = (dir == "." || Pathname.new(f).absolute?) ? f : "#{escape_glob_characters(dir)}/#{f}"
          # This is where we override default behavior
          asset_file = Java::AssetPipeline::AssetHelper.fileForFullName(path)
          asset_file ? [[asset_file.getPath(),s]] : []
        end
        found = Sass::Util.flatten(found, 1)

        return if found.empty?

        if found.size > 1 && !@same_name_warnings.include?(found.first.first)
          found.each {|(f, _)| @same_name_warnings << f}
          relative_to = Pathname.new(dir)
          if options[:_line]
            # If _line exists, we're here due to an actual import in an
            # import_node and we want to print a warning for a user writing an
            # ambiguous import.
            candidates = found.map {|(f, _)| "    " + Pathname.new(f).relative_path_from(relative_to).to_s}.join("\n")
            Sass::Util.sass_warn <<WARNING
WARNING: On line #{options[:_line]}#{" of #{options[:filename]}" if options[:filename]}:
  It's not clear which file to import for '@import "#{name}"'.
  Candidates:
#{candidates}
  For now I'll choose #{File.basename found.first.first}.
  This will be an error in future versions of Sass.
WARNING
          else
            # Otherwise, we're here via StalenessChecker, and we want to print a
            # warning for a user running `sass --watch` with two ambiguous files.
            candidates = found.map {|(f, _)| "    " + File.basename(f)}.join("\n")
            Sass::Util.sass_warn <<WARNING
WARNING: In #{File.dirname(name)}:
  There are multiple files that match the name "#{File.basename(name)}":
#{candidates}
WARNING
          end
        end
        found.first
      end

    def _find(dir, name, options)

        full_filename, syntax = Sass::Util.destructure(find_asset_file(dir, name, options))

        if !full_filename
        	return _find_filesystem(dir,name, options)
        end

        options[:syntax] = syntax
        options[:filename] = full_filename
        options[:importer] = self
        asset_file = Java::AssetPipeline::AssetHelper.fileForFullName(full_filename)
        Sass::Engine.new(Java::AssetPipelineSass::SassProcessor.convertStreamToString(asset_file.getInputStream()), options)
    end

    def _find_filesystem(dir,name, options)
    	full_filename, syntax = Sass::Util.destructure(find_real_file(dir, name, options))
    	return unless full_filename
    	options[:syntax] = syntax
        options[:filename] = full_filename
        options[:importer] = self
        Sass::Engine.new(File.read(full_filename), options)
    end
end
				