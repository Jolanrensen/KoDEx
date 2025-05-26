<img src="KoDExColored.svg" align="right" width="75" height="75" alt="KoDEx">

# `/** KoDEx */`: Kotlin Documentation Extensions
[![Maven metadata URL](https://img.shields.io/maven-metadata/v?label=Gradle%20Plugin&metadataUrl=https%3A%2F%2Fplugins.gradle.org%2Fm2%2Fnl%2Fjolanrensen%2FdocProcessor%2Fnl.jolanrensen.docProcessor.gradle.plugin%2Fmaven-metadata.xml)](https://plugins.gradle.org/plugin/nl.jolanrensen.docProcessor)

[![IntelliJ Plugin](https://img.shields.io/jetbrains/plugin/v/27473?label=IntelliJ%20Plugin)
](https://plugins.jetbrains.com/plugin/27473)

KDoc Preprocessor [Gradle Plugin](https://plugins.gradle.org/plugin/nl.jolanrensen.docProcessor) and [IDEA plugin (Beta)](https://plugins.jetbrains.com/plugin/26250)

Kotlin libraries use KDoc to document their code and especially their public API. This allows users
to understand how to use the library and what to expect from it. However, writing KDoc can be a tedious task, especially
when you have to repeat the same information in multiple places. KoDEx allows us to write the
information only once and then include it in multiple places.

KoDEx works in preprocessing 'waves'; In each wave, all KDoc comments are processed by a single preprocessor before
passing on the results to the next.
A preprocessor in KoDEx can modify its custom tags in your KDoc comments or change the entirety of the comment itself.
See more about the [preprocessors](#preprocessors) below.

Note: `{@inline tags}` now work in KDocs too! Plus, `{@tags {@inside tags}}` are supported as well.
(Javadoc may be supported, but since I have no need for it personally, I don't plan on supporting it explicitly.)

KoDEx comes in the form of two plugins:

- The KoDEx [IDEA Plugin](https://plugins.jetbrains.com/plugin/26250) allows you to preview the rendered KDocs in the IDE
and provides completion, highlighting, and descriptions for the new tags.

- The KoDEx [Gradle Plugin](https://plugins.gradle.org/plugin/nl.jolanrensen.docProcessor) allows you to actually process
  all KDoc comments in your project with the custom preprocessors and obtain the modified sources afterward.

(KoDEx is not a Dokka plugin, meaning you can actually get a `sources.jar` file with the modified comments instead of just
having the comments modified in a `javadoc.jar` or a Dokka HTML website).

## Example

You can find a little video demo [right here](https://drive.google.com/file/d/1ddcU9bTLlj9IQ52fTei1ajeg0YfzEwkJ/view?usp=sharing).

### What you write:

<div style="width: 100%;">
  <img src="whatYouWrite.svg" style="width: 100%;" alt="Click to see the source">
</div>

### What you get:

<div style="width: 100%;">
  <img src="whatYouGet.svg" style="width: 100%;" alt="Click to see the source">
</div>

## Used By

This plugin is used by [Kotlin DataFrame](https://github.com/Kotlin/dataframe), to make it possible
to document and update the wide range of function overloads present in the library due to its DSL-like nature.

Let me know if you're using it in your project too!

## Preprocessors

Preprocessors are run one at a time, in order, on all KDoc comments in the sources.
If a preprocessor is a tag processor, it will process only its own tags in the following order:

- Inline tags
    - depth-first
    - top-to-bottom
    - left-to-right
- Block tags
    - top-to-bottom

Included preprocessors are:

| Description                                                                                                                                                                                                                                                                          | Name                            |
|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|
| `@include` tag to include other documentation into your KDoc.<br/>Used like `@include [Reference.To.Element]`.                                                                                                                                                                       | `INCLUDE_DOC_PROCESSOR`         |
| `@includeFile` tag to include the entire content of a file into your KDoc.<br/>Used like `@includeFile (./path/to/file)`.                                                                                                                                                            | `INCLUDE_FILE_DOC_PROCESSOR`    |
| `@set` / `@get` (or `$`) tags to define and retrieve variables within a KDoc. Powerful in combination with `@include`.<br/>Used like `@set KEY some content`, `@get KEY some default`.<br/>Shortcuts for `{@get .}` are `$KEY`, `$KEY=default`, `${KEY}`, and `${KEY=some default}`. | `ARG_DOC_PROCESSOR`             |
| `@comment` tag to comment out parts of your modified KDoc.<br/>Used like `@comment Some comment text`.                                                                                                                                                                               | `COMMENT_DOC_PROCESSOR`         |
| `@sample` / `@sampleNoComments` tags to include code samples into your KDoc.<br/>Used like `@sample [Reference.To.Element]`.<br/>If present, only code in between `// SampleStart` and `// SampleEnd` is taken. `@sampleNoComments` excludes KDoc from the sample.                   | `SAMPLE_DOC_PROCESSOR`          |
| `@exportAsHtmlStart` / `@exportAsHtmlEnd` to mark a range of KDoc for the `@ExportAsHtml` annotation.                                                                                                                                                                                | `EXPORT_AS_HTML_DOC_PROCESSOR`  |
| A processor that removes all escape characters ("\\") from your KDoc comments.                                                                                                                                                                                                       | `REMOVE_ESCAPE_CHARS_PROCESSOR` |
| A processor that removes all KDoc comments.                                                                                                                                                                                                                                          | `NO_DOC_PROCESSOR`              |
| A processor that adds a `/** TODO */` comment wherever there is no KDoc comment.                                                                                                                                                                                                     | `TODO_DOC_PROCESSOR`            |

See the [Wiki](https://github.com/Jolanrensen/KoDEx/wiki/Notation) for more information on the tags.

Of course, you can also try to make your own preprocessor (see [Custom Processors](#custom-processors)).
For instance, you could make a processor that makes all KDoc comments uppercase,
a tag processor that automatically inserts URLs to your website, or simply a processor that produces
errors or warnings for incorrect doc usage.

The sky is the limit :)

## `@ExcludeFromSources` annotation

If you want to exclude any annotatable element from the `sources.jar`. 
Create an annotation class named exactly "`ExcludeFromSources`" 
(you can copy the code from [here](./kodex-common/src/main/kotlin/nl/jolanrensen/kodex/annotations/ExcludeFromSources.kt))
and annotate the elements you want to exclude with it.
This is especially useful for "temporary" documentation interfaces, only there
to provide documentation for other elements.


## `@ExportAsHtml` annotation

To export a KDoc comment as HTML, you can use the `@ExportAsHtml` annotation.
Create an annotation class named exactly "`ExportAsHtml`" and add the arguments `theme: Boolean` and 
`stripReferences: Boolean` (default both to `true`)
(you can copy the code from [here](./kodex-common/src/main/kotlin/nl/jolanrensen/kodex/annotations/ExportAsHtml.kt)).
Then, add the annotation to the element you want to export as HTML.

Inside the KDoc comment, you can mark a range of text to be exported as HTML by using the optional `@exportAsHtmlStart` 
and `@exportAsHtmlEnd` tags.

In the Gradle task the HTML will be generated in the folder specified in the `exportAsHtml` block of the
`ProcessDocTask` (see below).
In the IntelliJ plugin, a gutter icon will appear that can take you to the generated HTML file.

## How to get it

### From Gradle Plugins

In your project's `settings.gradle.kts` or `build.gradle` add:

```kts
pluginManagement {
    repositories {
        ..
        gradlePluginPortal()
    }
}
```

In `build.gradle.kts` or `build.gradle` add `id("nl.jolanrensen.kodex") version "{ VERSION }"`
to `plugins { .. }`.

### From sources

Clone the project and run `./gradlew publishToMavenLocal` in the source folder.

In your project's `settings.gradle.kts` or `settings.gradle` add:

```kts
pluginManagement {
    repositories {
        ..
        mavenLocal()
    }
}
```

In `build.gradle.kts` or `build.gradle` add `id("nl.jolanrensen.kodex") version "{ VERSION }"`
to `plugins { .. }`.

## How to use

Say you want to create a task that will run when you're making a sources.jar such that the modified files appear in the
Jar:

`build.gradle.kts`:

```kts
import nl.jolanrensen.kodex.gradle.*
import nl.jolanrensen.kodex.defaultProcessors.*
import org.gradle.jvm.tasks.Jar

..

plugins {
    id("nl.jolanrensen.kodex") version "{ VERSION }"
    ..
}

..

// Backup the kotlin source files location
val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories

// Create the processing task and point it to the right sources. 
// This can also be the test sources for instance.
val processKdocMain by creatingProcessDocTask(sources = kotlinMainSources) {

    // Optional. The target folder of the processed files. By default ${project.buildDir}/kodex/${taskName}.
    target = file(..)

    // Optional. The processors you want to use in this task. If unspecified, the default processors will be used.
    // The recommended order of default processors is as follows:
    processors = listOf(
        COMMENT_DOC_PROCESSOR, // The @comment processor
        INCLUDE_DOC_PROCESSOR, // The @include processor
        INCLUDE_FILE_DOC_PROCESSOR, // The @includeFile processor
        ARG_DOC_PROCESSOR, // The @set and @get / $ processor
        SAMPLE_DOC_PROCESSOR, // The @sample and @sampleNoComments processor
        EXPORT_AS_HTML_DOC_PROCESSOR, // The @exportAsHtmlStart and @exportAsHtmlEnd tags for @ExportAsHtml
        REMOVE_ESCAPE_CHARS_PROCESSOR, // The processor that removes escape characters

        "com.example.plugin.ExampleDocProcessor", // A custom processor if you have one, see below
    )

    // Optional. Send specific arguments to processors.
    arguments += ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false

    // Optional dependencies for this task. These dependencies can introduce custom processors.
    dependencies {
        plugin("com.example:plugin:SOME_VERSION")
    }

    // Optional, defines where @ExportAsHtml will put the generated HTML files. By default ${project.buildDir}/kodex/${taskName}/htmlExports.
    exportAsHtml {
      dir = file("../docs/StardustDocs/snippets")
    }
}

// Modify all Jar tasks such that before running the Kotlin sources are set to 
// the target of processKdocMain and they are returned back to normal afterwards.
tasks.withType<Jar> {
    dependsOn(processKdocMain)
    outputs.upToDateWhen { false }

    doFirst {
        kotlin {
            sourceSets {
                main {
                    kotlin.setSrcDirs(processKdocMain.targets)
                }
            }
        }
    }

    doLast {
        kotlin {
            sourceSets {
                main {
                    kotlin.setSrcDirs(kotlinMainSources)
                }
            }
        }
    }
}

..

// As a bonus, this will update dokka to use the processed files as sources as well.
tasks.withType<org.jetbrains.dokka.gradle.AbstractDokkaLeafTask> {
    dokkaSourceSets {
        all {
            sourceRoot(processKdocMain.target.get())
        }
    }
}
```

`build.gradle`:

```groovy
import nl.jolanrensen.kodex.gradle.*
import nl.jolanrensen.kodex.defaultProcessors.*
import org.gradle.jvm.tasks.Jar

..

plugins {
    id "nl.jolanrensen.kodex" version "{ VERSION }"
            ..
}

    ..

// Backup the kotlin source files location
def kotlinMainSources = kotlin.sourceSets.main.kotlin.sourceDirectories

// Create the processing task and point it to the right sources. 
// This can also be the test sources for instance.
def processKdocMain = tasks.register('processKdocMain', ProcessDocTask) {

    // Optional. The target folder of the processed files. By default ${project.buildDir}/kodex/${taskName}.
    target file(..)

    // Optional. The processors you want to use in this task. If unspecified, the default processors will be used.
    // The recommended order of default processors is as follows:
    processors(
        CommentDocProcessorKt.COMMENT_DOC_PROCESSOR, // The @comment processor
        IncludeDocProcessorKt.INCLUDE_DOC_PROCESSOR, // The @include processor
        IncludeFileDocProcessorKt.INCLUDE_FILE_DOC_PROCESSOR, // The @includeFile processor
        ArgDocProcessorKt.ARG_DOC_PROCESSOR, // The @set and @get / $ processor
        SampleDocProcessorKt.SAMPLE_DOC_PROCESSOR, // The @sample and @sampleNoComments processor
        ExportAsHtmlDocProcessorKt.EXPORT_AS_HTML_DOC_PROCESSOR, // The @exportAsHtmlStart and @exportAsHtmlEnd tags for @ExportAsHtml
        RemoveEscapeCharsProcessorKt.REMOVE_ESCAPE_CHARS_PROCESSOR, // The processor that removes escape characters

        "com.example.plugin.ExampleDocProcessor", // A custom processor if you have one, see below
    )

    // Optional. Send specific arguments to processors.
    arguments[IncludeArgDocProcessorKt.ARG_DOC_PROCESSOR] = false

    // Optional dependencies for this task. These dependencies can introduce custom processors.
    dependencies {
        plugin "com.example:plugin:SOME_VERSION"
    }

    // Optional, defines where @ExportAsHtml will put the generated HTML files. By default ${project.buildDir}/kodex/${taskName}/htmlExports.
    exportAsHtmlDir = file("../docs/StardustDocs/snippets")

}.get()

// Modify all Jar tasks such that before running the Kotlin sources are set to 
// the target of processKdocMain and they are returned back to normal afterwards.
tasks.withType(Jar).configureEach {
    dependsOn(processKdocMain)
    outputs.upToDateWhen { false }

    doFirst {
        kotlin {
            sourceSets {
                main {
                    kotlin.srcDirs = processKdocMain.targets
                }
            }
        }
    }

    doLast {
        kotlin {
            sourceSets {
                main {
                    kotlin.srcDirs = kotlinMainSources
                }
            }
        }
    }
}

    ..

// As a bonus, this will update dokka to use the processed files as sources as well.
    tasks.withType(org.jetbrains.dokka.gradle.AbstractDokkaLeafTask).configureEach {
        dokkaSourceSets.with {
            configureEach {
                sourceRoot(processKdocMain.target.get())
            }
        }
    }
```

### Recommended order of default processors

While you can use the processors in any order and leave out some or include others, the recommended order is as follows:

- `COMMENT_DOC_PROCESSOR`: The `@comment` processor
- `INCLUDE_DOC_PROCESSOR`: The `@include` processor
- `INCLUDE_FILE_DOC_PROCESSOR`: The `@includeFile` processor
- `ARG_DOC_PROCESSOR`: The `@set` and `@get` / `$` processor. This runs `@set` first and then `@get` / `$`.
- `SAMPLE_DOC_PROCESSOR`: The `@sample` and `@sampleNoComments` processor
- `EXPORT_AS_HTML_DOC_PROCESSOR`: The `@exportAsHtmlStart` and `@exportAsHtmlEnd` tags for `@ExportAsHtml`
- `REMOVE_ESCAPE_CHARS_PROCESSOR`: The processor that removes escape characters

The `@comment` processor is recommended to be the first processor, such that its contents are removed before any other
processor runs.
Next, we ensure that `@set`/`@get` are processed after `@include` and `@includeFile` such that any arguments
that appear by them are available for the `@set`/`@get` processor.
`@sample` and `@sampleNoComments` are recommended to be last of the tag processors, as processing of inline
tags inside comments of `@sample` might not be desired. Finally, the `REMOVE_ESCAPE_CHARS_PROCESSOR` is recommended to
be last to clean up any escape characters that might have been introduced by the user to evade some parts of the docs
from being processed.

## Regarding Tags

Block-tags in KDocs and JavaDocs are structured in a list-like structure and are thus also parsed and processed
like that.
For example, the following KDoc:

```kotlin
/**
 * Some extra text
 * @a [Test2]
 * Hi there!
 * @b source someFun
 * Some more text. {@c
 * @d [Test1] (
 * }
 * @e)
 */
```

will be split up in blocks as follows:

```
[
  "\nSome extra text",
  "@a [Test2]\nHi there!",
  "@b source someFun\nSome more text. (\n{@c [Test1] (\n}",
  "@e)\n",
]
```

This is also how tag processors receive their block-data (note that any newlines after the `@tag`
are also included as part of the tag data).

Most tag processors only require a tiny number of arguments. They can decide what to do when they receive more arguments
by the user.
Most tag processors, like `@include`, `@sample`, and `@includeFile` all have systems in place that
will preserve the content after the tag.
Take this into account when writing your own processors.

To avoid any confusion, it's usually easier to stick to `{@inline tags}` as then it's clear which part of the doc
belongs to the tag and what does not. Inline tags are processed before block tags per processor.

Take extra care when using tags that can introduce new tags, such as `@include`, as this will cause the structure
of the doc to change mid-processing. Very powerful, but also potentially dangerous.
If something weird happens, try to disable some processors to understand what's happening.

## How the Gradle Plugin Works

- The sources provided to the plugin are read and analyzed by
  [Dokka's default SourceToDocumentableTranslators](https://kotlin.github.io/dokka/1.6.0/developer_guide/extension_points/#creating-documentation-models).
- All [Documentables](https://kotlin.github.io/dokka/1.6.0/developer_guide/data_model/#documentable-model) are
  saved in a map by their path (e.g. `com.example.plugin.Class1.function1`) and their extension path.
- Next, the documentation contents, location in the file, and indents are collected from each documentable
  in the map.
- All processors are run in sequence on the collected documentables with their data:
    - All documentables are iterated over and tag processors, like `@include`, will replace all tags with new
      content.
- Finally, all files from the source are copied over to a destination folder, and if there are any modifications that
  need to be made in a file, the specified ranges for each documentation are replaced with the new documentation.

## Custom processors

You can create an extension for the Gradle plugin with your own processors by either extending the
abstract `TagDocProcessor` class or
implementing the `DocProcessor` interface, depending on how much control you need over the docs.

Make sure to depend on the right module by adding the following to your `build.gradle.kts` or `build.gradle` file:

```kts
repositories {
    ..
    gradlePluginPortal()
}

dependencies {
    ..
    implementation("nl.jolanrensen.kodex:kodex-common:{ VERSION }")
    compileOnly("nl.jolanrensen.kodex:kodex-gradle-plugin:{ VERSION }")
}
```

Let's create a small example processor:

```kotlin
package com.example.plugin

import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.processor.TagDocProcessor
import nl.jolanrensen.kodex.utils.getTagArguments

class ExampleDocProcessor : TagDocProcessor() {

    /** We'll intercept @example tags. */
    override val providesTags: Set<String> = setOf("example")

    /** How `{@inner tags}` are processed. */
    override fun processInlineTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processContent(tagWithContent)

    /** How `  @normal tags` are processed. */
    override fun processBlockTagWithContent(
        tagWithContent: String,
        path: String,
        documentable: DocumentableWrapper,
    ): String = processContent(tagWithContent)

    // We can use the same function for both processInnerTagWithContent and processTagWithContent
    private fun processContent(tagWithContent: String): String {

        // We can log stuff, if we want to, using https://github.com/oshai/kotlin-logging
        logger.info { "Hi from the example logs!" }

        // We can get the content after the @example tag.
        val contentWithoutTag = tagWithContent
            .getTagArguments(tag = "example", numberOfArguments = 1)
            .single()
            .trimEnd() // remove trailing whitespaces/newlines

        // While we can play with the other arguments, let's just return some simple modified content
        var newContent =
            "Hi from the example doc processor! Here's the content after the @example tag: \"$contentWithoutTag\""

        // Since we trimmed all trailing newlines from the content, we'll add one back if they were there.
        if (tagWithContent.endsWith("\n"))
            newContent += "\n"

        return newContent
    }
}
```

For the processor to be detectable, we need to add this to the
`src/main/resources/META-INF/services/nl.jolanrensen.kodex.processor.DocProcessor` file:

```
com.example.plugin.ExampleDocProcessor
```

and then publish the project somewhere it can be used by other projects.

Add the published project as dependency in your other project's `build.gradle.kts` file in your created
doc process task (as described in the [How to Use](#how-to-use) section), both in the dependencies
and in the `processors` list.

Now, if that project contains a function like:

```kotlin
/**
 * Main function.
 * @example Example
 */
fun main() {
    println("Hello World!")
}
```

The output will be:

```kotlin

/**
 * Main function.
 * Hi from the example doc processor! Here's the content after the @example tag: "Example"
 */
fun main() {
    println("Hello World!")
}
```

See the `defaultProcessor` folder in the project for more examples!

## IntelliJ Plugin (Beta)

Aside from a Gradle plugin, the project also contains an IntelliJ plugin that allows you to preview the rendered
documentation directly in the IDE.
![image](https://github.com/Jolanrensen/KoDEx/assets/17594275/7f051063-38c7-4e8b-aeb8-fa6cf14a2566)

It now also helps with writing the documentation by providing completion, highlighting, and descriptions for the tags.

Now on the [Marketplace](https://plugins.jetbrains.com/plugin/26250)!

You can also try building the plugin yourself from sources and installing it in IntelliJ.
The plugin in its current state is unconfigurable and just uses the default processors as shown in the sample above.
Also, it uses the IDE engine to resolve references.
This is because it's a lot faster than my own engine + Dokka, but it does mean that there might be some differences
with the preview and how it will look in the final docs. So, take this into account.

I'm still working on connecting it to the Gradle plugin somehow or providing a way to configure it correctly,
but until then, you can use it as is and be more efficient in your documentation writing!
