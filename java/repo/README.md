# AIF Local Repository

This Maven repository stores any jars or other repository content that is not available
in a public maven repository (e.g., a snapshot or pre-release version).  The AIF `pom.xml`
file refers to this repository, so installation is automatic with an `mvn install`.

Once the desired JAR is available in Maven central, you can delete it from the AIF local
repository and it will be pulled from the default maven repository.

Credit for this solution goes to 
[This StackOverflow answer](https://stackoverflow.com/questions/4955635/how-to-add-local-jar-files-to-a-maven-project/28762617#28762617).

## Adding additional jars to the repository

If you have a jarfile that is not available in a public maven repository, you can add
it to the AIF local repository with the following command:

```
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file  \
    -Dfile=/some/path/on/local/filesystem/org.xyz.myproduct-0.9.0-SNAPSHOT.jar \
    -DgroupId=org.xyz -DartifactId=org.xyz.myproduct \
    -Dversion=0.9.0-SNAPSHOT -Dpackaging=jar \
    -DlocalRepositoryPath=repo
```

For example, the shacl jar file (`1.2-SNAPSHOT`, since removed) was added with the following command:

```
mvn org.apache.maven.plugins:maven-install-plugin:3.0.0-M1:install-file \
   -Dfile=shacl/shacl-1.2.0-SNAPSHOT.jar \
   -DgroupId=org.topbraid -DartifactId=shacl \
   -Dversion=1.2.0-SNAPSHOT -Dpackaging=jar -DlocalRepositoryPath=repo
```
