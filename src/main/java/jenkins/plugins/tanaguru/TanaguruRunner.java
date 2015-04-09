/*
 *  Tanaguru Runner - Trigger Automated webpage assessmentfrom Jenkins 
 *  Copyright (C) 2008-2015  Tanaguru.org
 * 
 *  This file is part of Tanaguru.
 * 
 *  Tanaguru is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 * 
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *  Contact us by mail: open-s AT open-s DOT com
 */
package jenkins.plugins.tanaguru;

import hudson.FilePath;
import hudson.model.BuildListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

/**
 * 
 */
public class TanaguruRunner {

	public static final String scriptName = "tanaguru_runner.sh";
	private final String tgScriptName;
	private final String scenario;
	private final String scenarioName;
	private final int buildNumber;
	private final String referential;
	private final String level;
	private final String xmxValue;
	private final File contextDir;
	private final BuildListener listener;
	private final String projectName;
	private final String insertActScript = "insert_act.sh";
	private final String sqlProcedureScript = "create_contract_with_scenario_and_create_act.sql";
	private final String propertyFile = "tanaguru.properties";
	
	public String auditId;
	public String mark;
	public String nbPassed;
	public String nbFailed;
	public String nbFailedOccurences;
	public String nbNmi;
	public String nbNa;
	public String nbNt;

	private FilePath workspace;
	private String context;
	
	private TanaguruRunnerBuilder.DescriptorImpl descriptor;

	public TanaguruRunner(
			String tgScriptName,
			String scenarioName, 
			String scenario, 
			int buildNumber, 
			String referential, 
			String level, 
			File contextDir,
			String xmxValue,
			BuildListener listener,
			TanaguruRunnerBuilder.DescriptorImpl descriptor,
			String projectName) {
		this.tgScriptName = tgScriptName;    
		this.scenario = scenario;
		this.scenarioName = scenarioName;
		this.buildNumber = buildNumber;
		this.referential = referential;
		this.level = level;
		this.descriptor = descriptor;
		this.contextDir = contextDir;
		this.xmxValue = xmxValue;
		this.listener = listener;
		this.projectName = projectName;
	}

	public void callTanaguruService() throws IOException, InterruptedException {
		File logFile = null;
		boolean isDynamic = descriptor.getDynamic();
		if(isDynamic){
			try {
				ClassLoader classLoader = getClass().getClassLoader();
				File workspacedir = new File(workspace.toURI());

				//////////////////////////////////////////////
				// COPY SQL PROCEDURE TO JENKINS WORKSPACE ///
				//////////////////////////////////////////////
				File sqlProcedureFile = new File(classLoader.getResource("sql/" + sqlProcedureScript).toURI());
				FileUtils.copyFileToDirectory(sqlProcedureFile, workspacedir);

				/////////////////////////////////////
				//// COPY SCENARIO IN A FILE ////////
				/////////////////////////////////////
				File tempScenario = new File(workspacedir.getAbsolutePath() + "/" + scenarioName + buildNumber);
				FileUtils.writeStringToFile(tempScenario, stripLeadingAndTrailingQuotes(TanaguruRunnerBuilder.forceVersion1ToScenario(scenario)));

				/////////////////////////////////////
				//// EDIT ACT SCRIPT CONTENT ////////
				/////////////////////////////////////
				String scriptActContent = IOUtils.toString(classLoader.getResourceAsStream("sql/" + insertActScript))
						.replace("$host", "'" + TanaguruInstallation.get().getDatabaseHost() + "'")
						.replace("$user", "'" + TanaguruInstallation.get().getDatabaseLogin() + "'")
						.replace("$port", "'" + TanaguruInstallation.get().getDatabasePort() + "'")
						.replace("$passwd", "'" + TanaguruInstallation.get().getDatabasePassword() + "'")
						.replace("$db", "'" + TanaguruInstallation.get().getDatabaseName() + "'")
						.replace("$procedureFileName", "tmp/" + sqlProcedureScript);

				File tempFileAct = new File(workspacedir.getAbsolutePath() + "/" + insertActScript);
				FileUtils.writeStringToFile(tempFileAct, scriptActContent);

				/////////////////////////////////////
				//// EDIT TANAGURU SCRIPT CONTENT ///
				/////////////////////////////////////        		
				String scriptTanaguruRunnerContent = IOUtils.toString(classLoader.getResourceAsStream("sql/" + scriptName))
						.replace("$sqProcedureScript", "'" + sqlProcedureScript + "'")
						.replace("$insertActScript",  "'" + insertActScript + "'")
						.replace("$contextDir",  "'" + context + "'")
						.replace("$tmpFolderName",  "'tmp'")
						.replace("$tgScriptName",  "'" + tgScriptName + "'")
						.replace("$scenarioName",  "'" + scenarioName.replaceAll("'", "'\"'\"'") + "'")
						.replace("$buildNumber",  "'" + buildNumber + "'")
						.replace("$firefoxPath",  "'" + descriptor.getFirefoxPath() + "'")
						.replace("$referentiel",  "'" + referential + "'")
						.replace("$level",  "'" +level + "'")
						.replace("$displayPort",  "'" + descriptor.getDisplayPort() + "'")
						.replace("$scenarioType",  "'Scenario'")
						.replace("$xmxValue",  "'" + xmxValue + "'")
						.replace("$projectName",  "'" + projectName.replaceAll("'", "'\"'\"'") + "'")
						.replace("$tanaguruLogin",  "'" + TanaguruInstallation.get().getTanaguruLogin() + "'");

				File tempFileRunner = new File(workspacedir.getAbsolutePath() + "/" +  scriptName);
				FileUtils.writeStringToFile(tempFileRunner, scriptTanaguruRunnerContent);

				String user = descriptor.getCliUserName();
				String password = descriptor.getCliPassword();
				String host = descriptor.getCliHostName();

				uploadFileToSftpServer(user, password, host, context, workspacedir.getAbsolutePath() + "/" + scriptName);
				uploadFileToSftpServer(user, password, host, context + "/tmp/", workspacedir.getAbsolutePath() + "/" + insertActScript);
				uploadFileToSftpServer(user, password, host, context + "/tmp/", workspacedir.getAbsolutePath() + "/" + sqlProcedureScript);
				uploadFileToSftpServer(user, password, host, context + "/tmp/", workspacedir.getAbsolutePath() + "/" + scenarioName + buildNumber);

				execNixComAndGetRez(user, password, host, "chmod -R 775 " + context + "/" + scriptName, listener.getLogger());			
				execNixComAndGetRez(user, password, host, "chmod -R 775 " + context + "/tmp", listener.getLogger());

				// dos2unix has to be pre installed
				execNixComAndGetRez(user, password, host, "cd " + context + " && dos2unix " + scriptName, listener.getLogger());
				execNixComAndGetRez(user, password, host, "cd " + context + " && ./tanaguru_runner.sh", listener.getLogger());

				// Extract data and print out
				String propertiesTanaguru = execNixComAndGetRez(user, password, host, "cd " + context + " && cat tmp/tanaguru.properties", listener.getLogger());
				
				logFile = new File(workspacedir.getAbsolutePath() + "/" +  propertyFile);
				FileUtils.writeStringToFile(logFile, propertiesTanaguru);
				
				extractDataAndPrintOut(logFile, listener.getLogger());
				
				// Remove files from remote server
				execNixComAndGetRez(user, password, host, "cd " + context + " && rm -rf " + scriptName + " && rm -rf tmp/" + insertActScript + " tmp/" + sqlProcedureScript + " tmp/" + scenarioName + buildNumber, listener.getLogger());
				if (!descriptor.getIsDebug()) {
					execNixComAndGetRez(user, password, host, "cd " + context + " && rm -rf tmp/*", listener.getLogger());
				}
				
				// Remove temp files from workspace
				FileUtils.deleteQuietly(sqlProcedureFile);
				FileUtils.deleteQuietly(tempScenario);
				FileUtils.deleteQuietly(tempFileAct);
				FileUtils.deleteQuietly(tempFileRunner);
				FileUtils.deleteQuietly(logFile);
				
			} catch (Exception e) {
				// TODO: handle exception
			}

		}else{
			File scenarioFile = 
					TanaguruRunnerBuilder.createTempFile(contextDir, scenarioName + buildNumber, 
							TanaguruRunnerBuilder.forceVersion1ToScenario(scenario));

			logFile = TanaguruRunnerBuilder.createTempFile(
							contextDir,
							"log-" + new Random().nextInt()+".log", 
							"");
			ProcessBuilder pb = new ProcessBuilder(
					tgScriptName,
					"-f", descriptor.getFirefoxPath(),
					"-r", referential,
					"-l", level,
					"-d", descriptor.getDisplayPort(),
					"-x", xmxValue,
					"-o", logFile.getAbsolutePath(),
					"-t", "Scenario",
					scenarioFile.getAbsolutePath());

			pb.directory(contextDir);
			pb.redirectErrorStream(true);
			Process p = pb.start();

			p.waitFor();

			extractDataAndPrintOut(logFile, listener.getLogger());

			if (!descriptor.getIsDebug()) {
				FileUtils.deleteQuietly(logFile);
			}

			FileUtils.deleteQuietly(scenarioFile);
		}
	}

	/**
	 * 
	 * @param logFile
	 * @param ps
	 * @throws IOException 
	 */
	public void extractDataAndPrintOut(File logFile, PrintStream ps) throws IOException {
		System.out.println("HERE WE GO LETS EXTRACT THE FILE");
		ps.println("");
		boolean isFirstMark = true;
		boolean isFirstNbPassed = true;
		boolean isFirstNbFailed = true;
		boolean isFirstNbFailedOccurences = true;
		boolean isFirstNbNmi = true;
		boolean isFirstNbNa = true;
		boolean isFirstNbNt = true;
		for (String line : FileUtils.readLines(logFile)) {
			if (StringUtils.startsWith(line, "Subject")) {
				ps.println("");
				ps.println(line);
			} else if (StringUtils.startsWith(line, "Audit terminated")) {
				ps.println(line);
			} else if (StringUtils.startsWith(line, "RawMark")) {
				ps.println(line.replace("RawMark", "Mark"));
				if (isFirstMark) {
					mark = StringUtils.substring(line, StringUtils.indexOf(line, ":")+1).replaceAll("%", "").trim();
					System.out.println("MARK : " + mark);
					isFirstMark = false;
				}
			} else if (StringUtils.startsWith(line, "Nb Passed")) {
				ps.println(line); 
				if (isFirstNbPassed) {
					nbPassed = StringUtils.substring(line, StringUtils.indexOf(line, ":")+1).trim();
					System.out.println("nbPassed " + nbPassed);
					isFirstNbPassed = false;
				}
			} else if (StringUtils.startsWith(line, "Nb Failed test")) {
				ps.println(line);
				if (isFirstNbFailed) {
					nbFailed = StringUtils.substring(line, StringUtils.indexOf(line, ":")+1).trim();
					System.out.println("nbFailed " + nbFailed);
					isFirstNbFailed = false;
				}
			} else if (StringUtils.startsWith(line, "Nb Failed occurences")) {
				ps.println(line);
				if (isFirstNbFailedOccurences) {
					nbFailedOccurences = StringUtils.substring(line, StringUtils.indexOf(line, ":")+1).trim();
					System.out.println("nbFailedOccurences " + nbFailedOccurences);
					isFirstNbFailedOccurences = false;
				}
			} else if (StringUtils.startsWith(line, "Nb Pre-qualified")) {
				ps.println(line);
				if (isFirstNbNmi) {
					nbNmi = StringUtils.substring(line, StringUtils.indexOf(line, ":")+1).trim();
					System.out.println("nbNmi " + nbNmi);
					isFirstNbNmi= false;
				}
			} else if (StringUtils.startsWith(line, "Nb Not Applicable")) {
				ps.println(line);
				if (isFirstNbNa) {
					nbNa = StringUtils.substring(line, StringUtils.indexOf(line, ":")+1).trim();
					System.out.println("nbNa " + nbNa);
					isFirstNbNa = false;
				}
			} else if (StringUtils.startsWith(line, "Nb Not Tested")) {
				ps.println(line);
				if (isFirstNbNt) {
					nbNt = StringUtils.substring(line, StringUtils.indexOf(line, ":")+1).trim();
					System.out.println("nbNt " + nbNt);
					isFirstNbNt = false;
				}
			} else if (StringUtils.startsWith(line, "Audit Id")) {
				ps.println(line);
				auditId = StringUtils.substring(line, StringUtils.indexOf(line, ":")+1).trim();
				System.out.println("auditId " + auditId);
			}
		}
		ps.println("");
		System.out.println("EEEEEENNNNNNNNNNNDDDDDDDD");
	}

	public String uploadFileToSftpServer(String user, String password, String host, String remotePath, String fileName) {
		int port = 22;
		String rez = "+!";
		File f = null;
		FileInputStream fis = null;

		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(user, host, port);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");

			session.connect();

			Channel channel = session.openChannel("sftp");
			channel.connect();
			ChannelSftp channelSftp = (ChannelSftp) channel;
			channelSftp.cd(remotePath);
			f = new File(fileName);
			fis = new FileInputStream(f);
			channelSftp.put(fis, f.getName());

			channel.disconnect();
			session.disconnect();
		}

		catch (Exception e) {
			rez = e.toString();
		}finally{
			if(fis!=null){
				try {
					fis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return rez;
	}

	public String execNixComAndGetRez(String user, String password, String host, String command, PrintStream ps) {
		int port = 22;
		String rez = "+!";

		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(user, host, port);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");

			session.connect();

			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command); //setting command

			channel.setInputStream(null);

			((ChannelExec) channel).setErrStream(System.err);

			InputStream in = channel.getInputStream();

			channel.connect();

			byte[] tmp = new byte[1024];
			while (true) {
				while (in.available() > 0) {
					int i = in.read(tmp, 0, 1024);
					if (i < 0)
						break;
					rez = new String(tmp, 0, i);
					ps.println(rez);
				}
				if (channel.isClosed()) {
					break;
				}
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
					rez = e.toString();
				}
			}
			channel.disconnect();
			session.disconnect();
		}

		catch (Exception e) {
			rez = e.toString();
		}
		return rez;
	}

	static String stripLeadingAndTrailingQuotes(String str){
		if (str.startsWith("\"")){
			str = str.substring(1, str.length());
		}

		if (str.endsWith("\"")){
			str = str.substring(0, str.length() - 1);
		}

		return str;
	}


	public String outputTanaguruResults() {
		return toString();
	}

	public FilePath getWorkspace() {
		return workspace;
	}

	public void setWorkspace(FilePath workspace) {
		this.workspace = workspace;
	}

	public String getContext() {
		return context;
	}

	public void setContext(String context) {
		this.context = context;
	}

}