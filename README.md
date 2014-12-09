SASS Asset Pipeline
==========================
The `sass-asset-pipeline` is a plugin that provides SASS support for the asset-pipeline static asset management plugin via compass.

For more information on how to use asset-pipeline, visit [here](http://www.github.com/bertramdev/asset-pipeline).

Goals
-----

* Ability to add own gems / libraries or versions of sass/compass for the build chain
* Prepackaged sass/compass when not specified
* Ability to scan AssetResolver interfaces for finding SCSS Imports
* Sprite Generation
* Threading Support

Rubygems
--------

This plugin will create a series of interfaces that wrap the prepackaged version of `rubygems` that comes with `jruby`. It will define a working gem directory (isolated from the system GEM_HOME directory) with which it can install gems too and add to the load path. Depending on the framework in use, different means will be created to instruct this plugin which gems need installed. The goal is for gradle to be as simple as going:

```groovy
gems {
  gem 'sass', '3.2.7'
  gem 'compass', '1.0.1'
  gem 'compass-blueprint'
  //Maybe support bundler options similar to
  gem 'myplugin', [path: '/path/to/my/local/gem']
  //Or maybe pull from scm
  gem 'myrepoplugin', [git: 'git://giturl', ref: 'patch-1']
}
```