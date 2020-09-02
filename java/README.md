# Using the AIF Library

As of version 1.2.1, the AIF library is available via [OSSRH](https://search.maven.org/search?q=g:com.github.nextcenturycorporation) (Maven Central). To use the library, mark the AIF library as a dependency in your build script or tool. As an example, Gradle requires the following to be added to your `build.gradle` file:

    repositories {
      mavenCentral()
    }
    dependencies {
        compile 'com.github.nextcenturycorporation:aida-interchange:latest.release'
    }

The above will add the latest release to your project.

In your code, import classes from the `com.ncc.aif` package.
Then, create a model, add entities, relations, and events to the
model, and then write the model out.

The file `src/test/java/com/ncc/aif/ExamplesAndValidationTests.java`
has a series of examples showing how to add things to the model.  The
`src/test/java/com/ncc/aif/ScalingTest.java` file has examples of how
to write the model out.

# Installation (No longer required)

As of version 1.2.1, the library no longer needs to be installed. However, these instructions are still applicable should you want to build a non-released version.

AIF is built with Java version 11.  You can download JDL 11 from several places:
* [Oracle JDK 11](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html)
(but be sure to read their [licensing FAQ](https://www.oracle.com/technetwork/java/javase/overview/oracle-jdk-faqs.html));
* [Oracle OpenJDK 11](http://openjdk.java.net/projects/jdk/11/);
* [AdoptOpenJDK](https://adoptopenjdk.net/?variant=openjdk11&jvmVariant=hotspot) (see their [Migration Guide](https://adoptopenjdk.net/migration.html))

If your current code is based on a version of Java earlier than Java 11, you may need to update your code as well.
If you must remain in an earlier Java environment (JDK 8 through 10), see the section entitled,
[Building AIF with an earlier version of Java](#building-aif-with-an-earlier-version-of-java).

To install the Java code, do `mvn install` from the `java` directory in this repository using Apache Maven.
Repeat this if you pull an updated version of the code. You can run the tests,
which should output the examples, by doing `mvn test`.

# Running the Ontology Resource Generator

To generate the resource variables from a particular ontology file, please refer to
the README located at `src/main/java/com/ncc/aif/ont2javagen/README.md`.

# Additional Information about Individual Ontologies

There is another README located at `src/main/resources/com/ncc/aif/ontologies/README.md` that gives a description about each of the ontology files currently available in AIF.

# Developing

If you need to edit the Java code:
 1. Install IntelliJ IDEA.
 2. "Import Project from Existing Sources"
 3. Choose the `pom.xml` for this repository and accept all defaults.

You should now be ready to go.

# Building AIF with an earlier version of Java

To build AIF with Java 9 or 10, change the value of the `<release>` tag in the `pom.xml` file to `9` or
`10` respectively.

To build with Java 8, replace the `maven-compiler-plugin`'s `<configuration>` tag with:

```
<configuration>
  <source>8</source>
  <target>8</target>
  <compilerArgs>
    <arg>-Xbootclasspath:/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar</arg>
  </compilerArgs>
</configuration>
```

Set the `-Xbootclasspath` to the path of your Java 8 runtime jar file (rt.jar).

# Documentation

To generate the javadoc documentation, navigate to the `java` directory in the AIDA-Interchange-Format project. Run the following command:

```bash
$ javadoc -d build/docs/javadoc/ src/main/java/com/ncc/aif/*.java
```
This script will generate documentation in the form of HTML and place it within the `build/docs/javadoc` folder.
