Liquid Rods
=======================

A minimalistic templating library inspired by Mustache/Handlebars and reusing Liquid's tag syntax, hence the name.
Out of the box, `Liquidrods` comes with the following features:

* Support for iteration (via the `{% for coll %}` tag) and conditionals (`{% if` tag)
* Support for template inheritance (via the `{% extends file %}`, `{% placeholder id %}` and `{% define id %}` tags) and template inclusion (`{% include field %}` tag)
* Variables: `{{var}}` (automatically escaped) and `{{{var}}}` (raw). the escaping logic is pluggable.
* Flexible property accessor syntax: `object.field.subField.'map.key'.anotherField` that works with maps, fields, getters, methods
* Powerful helper mechanism to keep the property accessors language minimalistic without sacrifying power: for `obj.x`, if x is not found, the resolver will look for a `x` method that takes `obj`as a parameter. See the example below for more detailed examples.


Using this library
------------------

To use this library in your projects, add the following to the `dependencies` section of your
`pom.xml` (liquidrods is in central):

```xml
<dependency>
  <groupId>com.plecting</groupId>
  <artifactId>liquidrods</artifactId>
  <version>1.0.0</version>
</dependency>
```

The API's entry point is the `liquidrods.Liquidrods` class. Start by creating an instance, parse a template into a `Template`object, and then call `render` on the latter:

```java
Liquidrods rods = new Liquidrods();
Template template = rods.parse("list.html");
template.render(model, writer);
```

The parse method comes in two flavours:

* `parse(String name)`: where name is a template name. You can configure Liquidrods with a `TemplateLoader` so that it knows how to load templates by name. By default, it'll look for the template in the classpath.
* `parse(Reader template)`: there. Nuff said.

Both `parse` method variants return a `Template` object ready to be used to render the template. To do so, you'll need to call the `render` method and provide:

* A model: the data backing the template. It can be anything: a map, a list, or any other object.
* A writer: where `Liquidrods` will output the rendered template.




Template syntax
-------

Besides raw text, `Liquidrods` templates can contain tags and variables.

### Variables

Variables are enclosed in double braces (or mustaches if you prefer), e.g.:

```html
<h1>{{todo.title}}</h1>
```

By default, the variable values will get escaped by Liquidrods. To output the raw value (you know what you're doing rght ?), you can use triple braces:

```html
<h1>{{{todo.title}}}</h1>
```

That's it. No filters, no operators, nothing fancy. The only thing you can put between the braces is a property selector in the model object. The selectors syntax is explained below.

### Tags

Tags uses Liquid's syntax, e.g.:

```html
<ul>
    {% for coll %}
        <li>I'm a teapot</li>
    {% end %}
</ul>
```

A more formal syntax definition:

```
{% tagName [optionalParameter] %}
[
tagBody
{% end [tagName] %}
]
```

`[` and `]` are used to enclose optional parts. A tag has a name (a word, without spaces) and an optional parameter. The parameter can be anything. It's up to the tag handler (the code behind) to handle it.
Optionnally, a tag can also have a close section (it'll depend on the tag handler), in which case it'll also enclose a tag body. The close section is defined using `{% ... %}` markers with the word `end` in it. You can alos, if you wish, add the opening tag name. For example, you can close a `for` tag in either those 2 ways:


```html
{% for coll %}
...
{% end %}
```

or

```html
{% for coll %}
...
{% end for %}
```

### properties selectors

Variables (could also be tag parameters) reference a property in the model using a selector. The selector syntax could be approximated using this grammar:

```
selector := segment ("." segment)*
segment  := current | ID | "'" segment "'"
current  := "." | "this"
ID       := [^\.']+
```

Or in plain English: a selector is composed of one or more segments seperated by dots `.`. A segment can be one of the constants `this` or `.`, or an identifier not containing dots nor quotes. Examples:

```
.
this
name
todo.title
validation.error-message
validation.'error.message'
'.'.description
validation.'error.message'.text-fr
```

The selector syntax is used to designate a path to follow in the model to retrive a value. Given a model object and a selector, the resolver follows this algorithm:

1. Split the selector into segments
2. For each segment:
    1. if the segment is `.` or `this`, return the model
    2. else:
        1. if the model is a Map, return the value keyed by segment (whether it exists or not (null))
        2. else, look in the model for a getter for the property. For a `title` property,look for `getTitle()` or `isTitle()`. If one exists, invoke it and return it's result.
        3. else, look in the model for a method named after the property. For a `title` property,look for a `title()` method. If it exists, invoke it and return it's result.
        4. else, look in the model for a field named after the property. If one is found, return it's value.
        5. finally, look in the **root model** for a method named after the property that takes the model as a parameter.

The root model is the model of the root context.

T.B.C.

License
-------

See `LICENSE` for details (hint: it's MIT).

Building
--------

You need a Java 6 (or newer) environment and Maven installed:

```
$ mvn --version
Apache Maven 3.0.3 (r1075438; 2011-02-28 18:31:09+0100)
Maven home: /usr/share/maven
Java version: 1.7.0_05, vendor: Oracle Corporation
Java home: /Library/Java/JavaVirtualMachines/1.7.0.jdk/Contents/Home/jre
Default locale: en_US, platform encoding: UTF-8
OS name: "mac os x", version: "10.8.2", arch: "x86_64", family: "mac"
```

You should now be able to do a full build of `liquidrods`:

```
$ git clone git://github.com/jawher/liquidrods.git
$ cd liquidrods
$ mvn clean install
```

Troubleshooting
---------------

Please consider using [Github issues tracker](http://github.com/jawher/liquidrods/issues) to submit bug reports or feature requests.
