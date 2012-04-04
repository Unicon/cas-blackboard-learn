package net.unicon.blackboard.authentication;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Jdk14Logger;

public final class BlackboardCasConfigurationManager {

	private static String getBackupDate(){
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
		String backupDate = fmt.format(new Date());
		return backupDate;
	}
	
	public static void main(final String[] args) throws Exception {

		File backupDir = new File("backup");
		backupDir.mkdir();
		
		final String logImpl = Jdk14Logger.class.getName();

		LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", logImpl);
		final Log logger = LogFactory.getLog(BlackboardCasConfigurationManager.class);

		logger.info("Loading local build properties...");
		final Configuration buildProperties = new PropertiesConfiguration("build.properties");

		final String blackboardHome = buildProperties.getString("blackboard.home");
		final File blackboardConfigFile = new File(blackboardHome, "config/bb-config.properties");


		File backupFile = new File(backupDir, "backup-bb-config-" + getBackupDate() + ".backup");
				
		logger.info("Creating backup of bb-config property file. Copied to " + backupFile.getCanonicalPath());
		FileUtils.copyFile(blackboardConfigFile, backupFile);

		logger.info("Loading blacboard configuration properties from " + blackboardConfigFile.getCanonicalPath());
		final PropertiesConfiguration blackboardConfigProperties = new PropertiesConfiguration(blackboardConfigFile);
		blackboardConfigProperties.setAutoSave(false);
		blackboardConfigProperties.setDelimiterParsingDisabled(true);

		final String blackboardAuthType = blackboardConfigProperties.getString("bbconfig.auth.type");
		logger.info("Current bbconfig auth type is set to " + blackboardAuthType);

		if (!blackboardAuthType.equals(CasAuthenticationModule.DEFAULT_CAS_AUTH_TYPE)) {
			blackboardConfigProperties.setProperty("bbconfig.auth.type", CasAuthenticationModule.DEFAULT_CAS_AUTH_TYPE);
			logger.info("Set bbconfig.auth.type to " + blackboardConfigProperties.getString("bbconfig.auth.type"));
		}

		blackboardConfigProperties.setProperty("bbconfig.tomcat.debug.enable", Boolean.TRUE.toString());
		logger.info("Set bbconfig.tomcat.debug.enable to "
				+ blackboardConfigProperties.getString("bbconfig.tomcat.debug.enable"));

		logger.info("Saving blackboard config properties to " + blackboardConfigFile.getCanonicalPath());
		blackboardConfigProperties.save();

		logger.info("Loading local cas-authentication properties...");
		final Configuration casAuthenticationProperties = new PropertiesConfiguration("dist/cas-authentication.properties");

		final File blackboardAuthConfigProperties = new File(blackboardHome, "config/authentication.properties");

		backupFile = new File(backupDir, "backup-authentication-" + getBackupDate() + ".backup");
		logger.info("Creating backup of authentication property file. Copied to " + backupFile.getCanonicalPath());
		FileUtils.copyFile(blackboardAuthConfigProperties, backupFile);

		logger.info("Loading blackboard cas-authentication properties from "
				+ blackboardAuthConfigProperties.getCanonicalPath());
		final PropertiesConfiguration blackboardAuthenticationProperties = new PropertiesConfiguration(
				blackboardAuthConfigProperties);
		blackboardAuthenticationProperties.setAutoSave(false);
		blackboardAuthenticationProperties.setDelimiterParsingDisabled(true);

		logger.info("Appending cas authentication settings to blackboard authentication.properties");
		final Iterator<?> it = casAuthenticationProperties.getKeys();
		while (it.hasNext()) {
			final String key = it.next().toString();
			final String value = casAuthenticationProperties.getProperty(key).toString();

			logger.info("Set " + key + "=" + value);
			blackboardAuthenticationProperties.setProperty(key, value);
		}

		logger.info("Saving blackboard authentication properties to " + blackboardConfigFile.getCanonicalPath());
		blackboardAuthenticationProperties.save();
		logger.info("Done.");
	}
}
