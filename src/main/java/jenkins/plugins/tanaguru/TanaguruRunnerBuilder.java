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

import com.sebuilder.interpreter.IO;
import com.sebuilder.interpreter.factory.ScriptFactory;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Result;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import java.io.File;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

/**
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link TanaguruRunnerBuilder} is created. The created instance is persisted
 * to the project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #scenarioName}) to remember the configuration.
 *
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 *
 * @author Jérôme Kowalczyk
 */
public class TanaguruRunnerBuilder extends Builder {

    private static final String PLOT_PLUGIN_YVALUE = "YVALUE=";
    private static final String TG_SCRIPT_NAME = "bin/tanaguru.sh";
    private static final String SQL_PROCEDURE_NAME = 
            "/sql/create_contract_with_scenario_and_create_act.sql";
    private static final String SQL_PROCEDURE_SCRIPT_NAME = 
            "create-contract-with-scenario-and-create-act.sql";
    private static final String INSERT_ACT_NAME = "/sql/insert_act.sh";
    private static final String INSERT_ACT_SCRIPT_NAME = "insert-act.sh";
    private static final String TMP_FOLDER_NAME = "tmp/";
    private static final String DEFAULT_XMX_VALUE = "256";
    
    private final String scenario;
    private final String scenarioName;
    private final String refAndLevel;
    private final String minMarkThresholdForStable;
    private final String maxFailedOccurencesForStable;
    private final String xmxValue;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public TanaguruRunnerBuilder(
            String scenarioName, 
            String scenario, 
            String refAndLevel, 
            String minMarkThresholdForStable,
            String maxFailedOccurencesForStable, 
            String xmxValue) {
        this.scenario = scenario;
        this.refAndLevel = refAndLevel;
        this.scenarioName = scenarioName;
        this.minMarkThresholdForStable = minMarkThresholdForStable;
        this.maxFailedOccurencesForStable = maxFailedOccurencesForStable;
        this.xmxValue = xmxValue;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     *
     * @return
     */
    public String getScenario() {
        return scenario;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     *
     * @return
     */
    public String getRefAndLevel() {
        return refAndLevel;
    }
    
    /**
     * We'll use this from the <tt>config.jelly</tt>.
     *
     * @return
     */
    public String getScenarioName() {
        return scenarioName;
    }
    
    /**
     * We'll use this from the <tt>config.jelly</tt>.
     *
     * @return
     */
    public String getMaxFailedOccurencesForStable() {
        return String.valueOf(maxFailedOccurencesForStable);
    }
    
    /**
     * We'll use this from the <tt>config.jelly</tt>.
     *
     * @return
     */
    public String getMinMarkThresholdForStable() {
        return String.valueOf(minMarkThresholdForStable);
    }
    
    /**
     * We'll use this from the <tt>config.jelly</tt>.
     *
     * @return
     */
    public String getXmxValue() {
        return String.valueOf(xmxValue);
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
      
        // This is where you 'build' the project.
        File contextDir = new File(getDescriptor().getTanaguruCliPath());
        if (!contextDir.exists()) {
            listener.getLogger().println("Le chemin vers le contexte d'exécution est incorrect");
            return false;
        }
        File scriptFile = new File(getDescriptor().getTanaguruCliPath() + "/" + TG_SCRIPT_NAME);
        if (!scriptFile.canExecute()) {
            listener.getLogger().println("Le script n'est pas exécutable");
            return false;
        }
        
        String spaceEscapedScenarioName=scenarioName.replace(' ','_');
        
        TanaguruRunner tanaguruRunner=
                new TanaguruRunner(
                        TG_SCRIPT_NAME,
                        spaceEscapedScenarioName,
                        scenario, 
                        build.getNumber(),
                        refAndLevel.split(";")[0],
                        refAndLevel.split(";")[1],
                        contextDir,
                        getDescriptor().getFirefoxPath(), 
                        getDescriptor().getDisplayPort(),
                        StringUtils.isBlank(xmxValue)? DEFAULT_XMX_VALUE : xmxValue,
                        listener,
                        getDescriptor().getIsDebug());

        tanaguruRunner.callTanaguruService();
        
        writeResultToWorkspace(tanaguruRunner, build.getWorkspace());
        
        linkToTanaguruWebapp(
                tanaguruRunner, 
                spaceEscapedScenarioName, 
                scenario, 
                contextDir, 
                build.getProject().getDisplayName());
        
        setBuildStatus(build, tanaguruRunner);
        
        return true;
    }
    
    /**
     * 
     * @param tanaguruRunner
     * @param scenario
     * @param workspace
     */
    private void linkToTanaguruWebapp(
            TanaguruRunner tanaguruRunner, 
            String scenarioName,
            String scenario,
            File contextDir,
            String projectName) throws IOException, InterruptedException {
        
        File insertProcedureFile = 
                TanaguruRunnerBuilder.createTempFile(
                        contextDir, 
                        SQL_PROCEDURE_SCRIPT_NAME,
                        IOUtils.toString(getClass().getResourceAsStream(SQL_PROCEDURE_NAME)));
        
        String script = IOUtils.toString(getClass().getResourceAsStream(INSERT_ACT_NAME))
            .replace("$host", TanaguruInstallation.get().getDatabaseHost())
            .replace("$user", TanaguruInstallation.get().getDatabaseLogin())
            .replace("$port", TanaguruInstallation.get().getDatabasePort())
            .replace("$passwd", TanaguruInstallation.get().getDatabasePassword())
            .replace("$db", TanaguruInstallation.get().getDatabaseName())
            .replace("$procedureFileName", TMP_FOLDER_NAME+SQL_PROCEDURE_SCRIPT_NAME);

        File insertActFile = 
                TanaguruRunnerBuilder.createTempFile(
                        contextDir, 
                        INSERT_ACT_SCRIPT_NAME,
                        script);
        
        ProcessBuilder pb = new ProcessBuilder(
                TMP_FOLDER_NAME+INSERT_ACT_SCRIPT_NAME,
                TanaguruInstallation.get().getTanaguruLogin(),
                projectName.replaceAll("'", "'\"'\"'"),
                scenarioName.replaceAll("'", "'\"'\"'"),
                TanaguruRunnerBuilder.forceVersion1ToScenario(scenario.replaceAll("'", "'\"'\"'")),
                tanaguruRunner.auditId);

        pb.directory(contextDir);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        p.waitFor();

        FileUtils.deleteQuietly(insertActFile);
        FileUtils.deleteQuietly(insertProcedureFile);
    }
    
    /**
     * Export atomic results from Tanaguru Analysis to enable graphic creation
     * through plot plugin.
     * 
     * @param tanaguruRunner
     * @param workspace 
     */
    private void writeResultToWorkspace(
            TanaguruRunner tanaguruRunner, 
            FilePath workspace) throws IOException, InterruptedException {
        
        File workspacedir = new File(workspace.toURI());
        
        writeValueToFile(tanaguruRunner.mark, "mark", workspacedir);
        writeValueToFile(tanaguruRunner.nbPassed, "passed", workspacedir);
        writeValueToFile(tanaguruRunner.nbFailed, "failed", workspacedir);
        writeValueToFile(tanaguruRunner.nbFailedOccurences, "failedOccurences", workspacedir);
        writeValueToFile(tanaguruRunner.nbNmi, "nmi", workspacedir);
        writeValueToFile(tanaguruRunner.nbNa, "na", workspacedir);
        writeValueToFile(tanaguruRunner.nbNt, "nt", workspacedir);
    }
    
    /**
     * 
     * @param value
     * @param valueType
     * @param workspacedir
     * @return
     * @throws IOException
     * @throws InterruptedException 
     */
    private void writeValueToFile(String value, String valueType, File workspacedir) 
            throws IOException, InterruptedException{
        File ntFile = new File (workspacedir.getAbsolutePath()+"/tanaguru-"+valueType+".properties");
        FileUtils.write(ntFile, PLOT_PLUGIN_YVALUE+value);
    }
    
    private void setBuildStatus(AbstractBuild build, TanaguruRunner tanaguruRunner) {
        if ((StringUtils.isBlank(minMarkThresholdForStable) || Integer.valueOf(minMarkThresholdForStable)<0) &&
                (StringUtils.isBlank(maxFailedOccurencesForStable) || Integer.valueOf(maxFailedOccurencesForStable)<0)) {
            build.setResult(Result.SUCCESS);
            return;
        }
        if (Integer.valueOf(minMarkThresholdForStable) > 0 &&
                Float.valueOf(tanaguruRunner.mark) < Integer.valueOf(minMarkThresholdForStable) ) {
            build.setResult(Result.UNSTABLE);
            return;
        }
        if (Integer.valueOf(maxFailedOccurencesForStable) > 0 && 
                Integer.valueOf(tanaguruRunner.nbFailedOccurences) > Integer.valueOf(maxFailedOccurencesForStable)) {
            build.setResult(Result.UNSTABLE);
            return;
        }
        build.setResult(Result.SUCCESS);
    }
    
    /**
     * Create a temporary file within a temporary folder, created in the
     * contextDir if not exists (first time)
     * @param contextDir
     * @param fileName
     * @param fileContent
     * @return
     * @throws IOException 
     */
    public static File createTempFile(File contextDir, String fileName, String fileContent) throws IOException {
        File contextDirTemp = new File (contextDir.getAbsolutePath()+"/tmp");
        if (!contextDirTemp.exists()) {
            if (contextDirTemp.mkdir()) {
                contextDirTemp.setExecutable(true);
                contextDirTemp.setWritable(true);
            }
        }
        File tempFile = new File(contextDirTemp.getAbsolutePath()+"/"+fileName);
        FileUtils.writeStringToFile(tempFile, fileContent);
        if (tempFile.exists()) {
            tempFile.setExecutable(true);
            tempFile.setWritable(true);
        }
        return tempFile;
    }
    
    /**
     * Change on-the-fly version of se-builder scenario from '2' to '1' to
     * ensure its compatibility 
     * @param scenario
     * @return the updated scenario
     */
    public static String forceVersion1ToScenario(String scenario)  {
        return scenario.replace("\"formatVersion\": 2", "\"formatVersion\":1")
                    .replace("\"formatVersion\":2", "\"formatVersion\":1");
    }
    
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return new ProjectTanaguruAction(project);
    }

    /**
     * Descriptor for {@link TanaguruRunnerBuilder}. Used as a singleton. The
     * class is marked as public so that it can be accessed from views.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private String pathToCli = "";
        private String displayPort = "";
        private String firefoxPath = "";
        private boolean isDebug = false;
        private TanaguruInstallation tanaguruInstallation;
        
        /**
         * In order to load the persisted global configuration, you have to call
         * load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'scenario'.
         *
         * @param value This parameter receives the value that the user has
         * typed.
         * @return Indicates the outcome of the validation. This is sent to the
         * browser.
         * <p>
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message will
         * be displayed to the user.
         * @throws javax.servlet.ServletException
         */
        public FormValidation doCheckScenario(@QueryParameter String value)
                throws ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please fill-in a not empty scenario");
            }
            
            try {
              IO.read(TanaguruRunnerBuilder.forceVersion1ToScenario(value));
            } catch (IOException ex) {
                return FormValidation.error("Please fill-in a valid scenario");
            } catch (ScriptFactory.SuiteException ex) {
                return FormValidation.error("Please fill-in a valid scenario");
            } catch (org.json.JSONException ex) {
                return FormValidation.error("Please fill-in a valid scenario");
            }
            return FormValidation.ok();
        }
        
        /**
         * Performs on-the-fly validation of the form field 'scenarioName'.
         *
         * @param value This parameter receives the value that the user has
         * typed.
         * @return Indicates the outcome of the validation. This is sent to the
         * browser.
         * <p>
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message will
         * be displayed to the user.
         * @throws java.io.IOException
         * @throws javax.servlet.ServletException
         */
        public FormValidation doCheckScenarioName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0) {
                return FormValidation.error("Please fill-in a name to the scenario");
            }
            return FormValidation.ok();
        }
        
        /**
         * Performs on-the-fly validation of the form field 'xmx Value'.
         *
         * @param value This parameter receives the value that the user has
         * typed.
         * @return Indicates the outcome of the validation. This is sent to the
         * browser.
         * <p>
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message will
         * be displayed to the user.
         * @throws javax.servlet.ServletException
         * @throws IOException 
         */
        public FormValidation doCheckXmxValue (@QueryParameter String value)
                throws IOException, ServletException {
            
            if (value.length() == 0) {
                return FormValidation.ok();
            }
            try {
                int xmxValue = Float.valueOf(value).intValue();
                if (xmxValue <= 64) {
                  return FormValidation.error("Please fill-in a Xmx value superior to default Xms value (64)");
                }
            } catch (NumberFormatException nfe) {
                return FormValidation.error("Please fill-in a valid Xmx value");
            }
            return FormValidation.ok();
        }
        
        /**
         * Performs on-the-fly validation of the form field 'Min Mark Threshold 
         * For Stable'.
         *
         * @param value This parameter receives the value that the user has
         * typed.
         * @return Indicates the outcome of the validation. This is sent to the
         * browser.
         * <p>
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message will
         * be displayed to the user.
         * @throws javax.servlet.ServletException
         * @throws IOException
         */
        public FormValidation doCheckMinMarkThresholdForStable (@QueryParameter String value)
                throws IOException, ServletException {
            
            if (value.length() == 0) {
                return FormValidation.ok();
            }
            try {
                int mark = Float.valueOf(value).intValue();
                if (mark > 100) {
                  return FormValidation.error("Please fill-in a valid minimal mark threshold");
                }
            } catch (NumberFormatException nfe) {
                return FormValidation.error("Please fill-in a valid minimal mark threshold");
            }
            return FormValidation.ok();
        }
        
        /**
         * Performs on-the-fly validation of the form field 'Max Failed 
         * Occurences For Stable'.
         *
         * @param value This parameter receives the value that the user has
         * typed.
         * @return Indicates the outcome of the validation. This is sent to the
         * browser.
         * <p>
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message will
         * be displayed to the user.
         * @throws javax.servlet.ServletException
         * @throws IOException
         */
        public FormValidation doCheckMaxFailedOccurencesForStable (@QueryParameter String value)
                throws IOException, ServletException {
            
            if (value.length() == 0) {
                return FormValidation.ok();
            }
            try {
                int mark = Integer.valueOf(value);
                if (mark > 65535) {
                  return FormValidation.error("Please fill-in a valid maximal number of failed occurences threshold");
                }
            } catch (NumberFormatException nfe) {
                return FormValidation.error("Please fill-in a valid maximal number of failed occurences threshold");
            }
            return FormValidation.ok();
        }


        /**
         * Fill-in values of the form field 'referential and level'.
         *
         * @param selection This parameter receives the value that the user has
         * typed.
         * @return the filled-in ListBoxModel 
         */
        public ListBoxModel doFillRefAndLevelItems(@QueryParameter String selection) {
            return new ListBoxModel(
                    new Option("Accessiweb2.2 : Bronze", "Aw22;Bz"),
                    new Option("Accessiweb2.2 : Argent", "Aw22;Ar"),
                    new Option("Accessiweb2.2 : Or", "Aw22;Or"),
                    new Option("Rgaa2.2 : A", "Rgaa22;Bz"),
                    new Option("Rgaa2.2 : AA", "Rgaa22;Ar"),
                    new Option("Rgaa2.2 : AAA", "Rgaa22;Or"),
                    new Option("Rgaa3 : A", "Rgaa30;Bz"),
                    new Option("Rgaa3 : AA", "Rgaa30;Ar"),
                    new Option("Rgaa3 : AAA", "Rgaa30;Or")
            );
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types 
            return true;
        }

        /**
         * @return the human readable name used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "Tanaguru Runner";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {

            pathToCli = formData.getString("tanaguruCliPath");
            displayPort = formData.getString("displayPort");
            firefoxPath = formData.getString("firefoxPath");
            tanaguruInstallation = 
                    new TanaguruInstallation(
                        formData.getString("webappUrl"), 
                        formData.getString("databaseHost"),
                        formData.getString("databasePort"),
                        formData.getString("databaseName"),
                        formData.getString("databaseLogin"),
                        formData.getString("databasePassword"),
                        formData.getString("tanaguruLogin")
                    );
            isDebug = formData.getBoolean("isDebug");

            save();
            return super.configure(req, formData);
        }

        /**
         * @return the path to the tanaguru cli script.
         */
        public String getTanaguruCliPath() {
            return pathToCli;
        }

        /**
         * @return the path to the tanaguru cli script.
         */
        public String getDisplayPort() {
            return displayPort;
        }

        /**
         * @return the path to the firefox instance.
         */
        public String getFirefoxPath() {
            return firefoxPath;
        }
        
        /**
         * @return whether the debug mode is activated
         */
        public boolean getIsDebug() {
            return isDebug;
        }

        /**
         * @return all configured {@link jenkins.plugins.tanaguru.TanaguruInstallation}
         */
        public TanaguruInstallation getInstallation() {
            return tanaguruInstallation;
        }
    }
}
