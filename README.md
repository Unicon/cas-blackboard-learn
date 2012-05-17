#CAS Authentication for Blackboard Learn 9.1

##Intro
"cas-blackboard-learn" is a [CAS](http://www.ja-sig.org/products/cas/) authentication module for [Blackboard](http://www.blackboard.com/). It utilizes the CAS 3.1 client 
library in order to connect to CAS 3.x servers (using the CAS 2.0 protocol).

This project is based on [CasAuthenticationModule](http://www.bris.ac.uk/ips-projects/portal/pilot/software/blackboard_cas/), 
developed by the University of Bristol and it is heavily influenced by the [blackboard-cas](http://code.google.com/p/blackboard-cas/) project on Google Code. 


##Configuration
See the `build.properties` file for settings and configuration. 

Next, customize the following blackboard files by adding the lines immediately following each filename listed below, (including any leading spaces):

```xml

-C:\blackboard\system\build\bin\launch-app.bat:

rem # CAS
set THIRD_PARTY_CP=%THIRD_PARTY_CP%;cas-bbauth.jar
set THIRD_PARTY_CP=%THIRD_PARTY_CP%;cas-client-core-3.2.1.jar
set THIRD_PARTY_CP=%THIRD_PARTY_CP%;xmlsec-1.3.0.jar
rem # CAS
	
-C:\blackboard\apps\collab-server\config\wrapper.conf.bb:

rem # CAS
wrapper.java.classpath.43=@@bbconfig.basedir@@/systemlib/cas-bbauth.jar
wrapper.java.classpath.44=@@bbconfig.basedir@@/systemlib/cas-client-core-3.2.1.jar
wrapper.java.classpath.45=@@bbconfig.basedir@@/systemlib/xmlsec-1.3.0.jar
rem # CAS

-C:\blackboard\apps\snapshot\config\env.cmd.bb:

rem # CAS
set CP=%CP%;%BBLIB%\cas-bbauth.jar
set CP=%CP%;%BBLIB%\cas-client-core-3.2.1.jar
set CP=%CP%;%BBLIB%\xmlsec-1.3.0.jar
rem # CAS

-C:\blackboard\apps\content-exchange\bin\content-exchange.bat.bb:

rem # CAS
set CP=%CP%;%BBLIB%\cas-bbauth.jar
set CP=%CP%;%BBLIB%\cas-client-core-3.2.1.jar
set CP=%CP%;%BBLIB%\xmlsec-1.3.0.jar
rem # CAS
```

##Build
You'll need an instance of Blackboard in `c:\blackboard` in order to build and [Apache Ant (1.8.2)](http://ant.apache.org/)  to run the build. 
Once you have configured everything, you can build with ant:

`c:\workspace\cas-blackboard-learn\ant init`


The init target will compile, package and deploy the CAS custom authentication module. Previous log files are also cleared out. 

Next, the build will attempt to stop Blackboard services. The Blackboard `Push Config` process is then executed to deploy custom changes to Blackboard. 
Services are automatically restarted once the `Push Config` step is done.

The `Push Config` step resets Blackboard property files back to their default state! The build will automatically readjust the property values in files
`config/bb-config.properties` and `config/authentication.properties` so Blackboard can recognize the new authentication mechanism with CAS. 

##Access
To log into Blackboard without using CAS SSO, append the parameter `disableCAS=1` at the end of the URL specified by 
the blackboard.url property, i.e. [http://blackboard.domain.edu/?disableCAS=1](http://blackboard.domain.edu/?disableCAS=1) 


##Logging
Inside the logs directory of the Blackboard installation folder, you may analyze the following files to 
examine and troubleshoot CAS behavior (where xyz indicates the file date):

* `bb-services-log-xyz.txt`
* Tomcat logs inside the tomcat directory (i.e. `stdout-stderror-xyz.log`)
* Collab server logs inside the `collab-server` directory.

##Upgrade Notes
If you plan to upgrade Blackboard to the next service pack, etc you'll need to make sure that certain CAS changes are disabled
before you run upgrade commands.

* Make sure `config/bb-config.properties` and `config/authentication.properties` are reset to their original version.
* Remove the `cas-common.classpath` file from `c:\blackboard\config\tomcat\classpath`. 
* Execute `Push Config` to enforce the changes.
* Upgrade Blackboard and repeat the instructions. (You'll have to make the classpath adjustments above again noted in the Configuration section, as 
the upgrade completely resets everything)

