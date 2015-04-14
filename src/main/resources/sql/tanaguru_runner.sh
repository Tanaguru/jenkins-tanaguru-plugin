#!/bin/bash

SQL_PROCEDURE_SCRIPT=$sqProcedureScript
INSERT_ACT_SCRIPT=$insertActScript;

CONTEXT_DIR=$contextDir

TMP_FOLDER_NAME=$tmpFolderName;
TG_SCRIPT_NAME=$tgScriptName;
SCENARIO_NAME=$scenarioName 
BUILD_NUMBER=$buildNumber
FIREFOX_PATH=$firefoxPath
REFERENTIAL=$referentiel
LEVEL=$level
DISPLAY_PORT=$displayPort
XMX_VALUE=$xmxValue
SCENARIO_TYPE=$scenarioType
PROJECT_NAME=$projectName

TANAGURU_LOGIN=$tanaguruLogin 


function run_tanaguru_script(){
	echo "Running Tanaguru Script..."   
	cd $CONTEXT_DIR
	LOG_FILE=`date +%d%m%y%H%M%S`
	echo "" > $TMP_FOLDER_NAME/$LOG_FILE.log
	chmod 775 $TMP_FOLDER_NAME/$LOG_FILE.log
	echo "LOG FILE" $TMP_FOLDER_NAME/$LOG_FILE.log "Created"
	
	$TG_SCRIPT_NAME -f $FIREFOX_PATH -r $REFERENTIAL -l $LEVEL -d $DISPLAY_PORT -x $XMX_VALUE -o $TMP_FOLDER_NAME/$LOG_FILE.log -t $SCENARIO_TYPE $TMP_FOLDER_NAME/$SCENARIO_NAME$BUILD_NUMBER
	
	AUDIT_ID=`cat $TMP_FOLDER_NAME/$LOG_FILE.log | grep --max-count=1 '^Audit Id' | sed 's/.* \(.*\)/\1/'`
	
	echo "Tanaguru.sh succesfully executed"
	
	echo `cat $TMP_FOLDER_NAME/$LOG_FILE.log | grep --max-count=1 '^Audit terminated'` > $TMP_FOLDER_NAME/tanaguru.properties 
	echo `cat $TMP_FOLDER_NAME/$LOG_FILE.log | grep --max-count=1 '^Audit Id'` >> $TMP_FOLDER_NAME/tanaguru.properties 
	echo `cat $TMP_FOLDER_NAME/$LOG_FILE.log | grep --max-count=1 '^RawMark'` >> $TMP_FOLDER_NAME/tanaguru.properties 
	echo `cat $TMP_FOLDER_NAME/$LOG_FILE.log | grep --max-count=1 '^Nb Passed'` >> $TMP_FOLDER_NAME/tanaguru.properties
	echo `cat $TMP_FOLDER_NAME/$LOG_FILE.log | grep --max-count=1 '^Nb Failed test'` >> $TMP_FOLDER_NAME/tanaguru.properties 
	echo `cat $TMP_FOLDER_NAME/$LOG_FILE.log | grep --max-count=1 '^Nb Failed occurences'` >> $TMP_FOLDER_NAME/tanaguru.properties 
	echo `cat $TMP_FOLDER_NAME/$LOG_FILE.log | grep --max-count=1 '^Nb Pre-qualified'` >> $TMP_FOLDER_NAME/tanaguru.properties 
	echo `cat $TMP_FOLDER_NAME/$LOG_FILE.log | grep --max-count=1 '^Nb Not Applicable'` >> $TMP_FOLDER_NAME/tanaguru.properties 
	echo `cat $TMP_FOLDER_NAME/$LOG_FILE.log | grep --max-count=1 '^Nb Not Tested'` >> $TMP_FOLDER_NAME/tanaguru.properties 
	
	#rm -f $TMP_FOLDER_NAME/$LOG_FILE.log
}

function link_to_tanaguru(){
	echo "Linking results to Tanaguru..."
	cd $CONTEXT_DIR
	if [ -f "$TMP_FOLDER_NAME/$SQL_PROCEDURE_SCRIPT" ]
	then
		if [ -f "$TMP_FOLDER_NAME/$INSERT_ACT_SCRIPT" ]
		then
			$TMP_FOLDER_NAME/$INSERT_ACT_SCRIPT "$TANAGURU_LOGIN" "$PROJECT_NAME" "$SCENARIO_NAME" "$TMP_FOLDER_NAME/$SCENARIO_NAME$BUILD_NUMBER" $AUDIT_ID
			echo "insert act script done with success" 
		else
			error_exit "The ACT SCRIPT doesn't exist!  Aborting."
		fi
	else
		error_exit "The SQL PROCEDURE SCRIPT doesn't exist!  Aborting."
	fi
}

function error_exit(){
	echo "$1" 1>&2
	exit 0
}

if [ -d $CONTEXT_DIR ]; then
	if [ -x "$CONTEXT_DIR/$TG_SCRIPT_NAME" ]; then 
		## Run Tanaguru Script
		run_tanaguru_script
		
		## Link result to Database tables
		link_to_tanaguru
		
		exit 1
	else 
		error_exit "Script file doesn't exist of is not executable !  Aborting."
	fi
else 
	error_exit "Context directory doesn't exist!  Aborting."
fi