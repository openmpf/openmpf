#!/usr/bin/perl

#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2019 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2019 The MITRE Corporation                                      #
#                                                                           #
# Licensed under the Apache License, Version 2.0 (the "License");           #
# you may not use this file except in compliance with the License.          #
# You may obtain a copy of the License at                                   #
#                                                                           #
#    http://www.apache.org/licenses/LICENSE-2.0                             #
#                                                                           #
# Unless required by applicable law or agreed to in writing, software       #
# distributed under the License is distributed on an "AS IS" BASIS,         #
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  #
# See the License for the specific language governing permissions and       #
# limitations under the License.                                            #
#############################################################################

use strict;
use warnings;
use Term::ANSIColor;

# sudo yum install perl-File-Find-Rule.noarch
use File::Find::Rule;
use File::Basename;

#	Define the package.
package MPF::Jenkins;


#	Define logging levels.
my $ALL		= 0;
my $TRACE	= 1;
my $DEBUG	= 2;
my $INFO	= 3;
my $WARN 	= 4;
my $ERROR	= 5;
my $FATAL	= 6;
my $OFF		= 7;
my $loggingLevel = $ALL;

#	Globals
my $nodeManagerName = "\"mpf-nodemanager-.*\.jar\"";



##########################################################################################
#
#	ACTIVE MQ FUNCTIONS
#
##########################################################################################
sub stopActiveMQ {
	if ((@_) != 1) {
		printFatal("Needed 1 argument: amqPath.\n");
		fatalExit();
	}
	my $amqPath = $_[0];
	my @ActiveMQList = ();

	printInfo("Checking for current instances of ActiveMQ...\n");

	#	If AcqiveMQ is running tell it to shut down.
	@ActiveMQList = `ps ax | grep \"activemq\" | grep -v \"grep\" | grep -v \"perl\"`;
	if((@ActiveMQList) > 0) {
		printWarn("ActiveMQ is currently running.  Shutting it down.\n");
		system "sudo $amqPath/bin/activemq stop";
	}
	sleep(2);

	#	If ActiveMQ is still running, use the kill command to terminate it.\n";
	@ActiveMQList = `ps ax | grep \"activemq\" | grep -v \"grep\" | grep -v \"perl\"`;
	foreach my $item (@ActiveMQList) {
		chomp $item;
		printWarn("Found: $item\n");
	}
	if((@ActiveMQList) > 0) {
		printError("ActiveMQ did not respond to the shutdown request.\n");
		foreach my $pid (@ActiveMQList) {
			$pid =~ s/^\s+|\s+$//g;
			my @tokens = split /\s/, $pid;
			$pid = $tokens[0];
			printError("Killing ActiveMQ pid $pid.\n");
			system "sudo kill -9 $pid";
		}
	}

	#	If ActiveMQ is still running, then somethine went wrong.  Exit.
	@ActiveMQList = `ps ax | grep \"activemq\" | grep -v \"grep\" | grep -v \"perl\"`;
	if((@ActiveMQList) > 0) {
		printFatal("ActiveMQ did not respond to the shutdown request.\n");
		fatalExit();
	} else {
		printInfo("\t\tCurrently shutdown.\n");
	}
}


sub cleanActiveMQ {
	if ((@_) != 1) {
		printFatal("Needed 1 argument: amqPath.\n");
		fatalExit();
	}
	my $amqPath = $_[0];

	printInfo("Cleaning out ActiveMQ KahaDB information.\n");
	stopActiveMQ($amqPath);
	system "sudo rm -rf $amqPath/data/kahadb";
}


sub runActiveMQ {
	if ((@_) != 1) {
		printFatal("Needed 1 argument: amqPath.\n");
		fatalExit();
	}
	my $amqPath = $_[0];
	my @ActiveMQList = ();

	printInfo("Starting ActiveMQ...\n"	);
	system "sudo $amqPath/bin/activemq start > /dev/null";
	sleep(5);

	#	Verify that AcqiveMQ is running...
	@ActiveMQList = `ps ax | grep \"activemq\" | grep -v \"grep\" | grep -v \"perl\"`;
	if((@ActiveMQList) == 0) {
		printFatal("ActiveMQ did not respond to the startup request.\n");
		fatalExit();
	}
	if((@ActiveMQList) > 1) {
		printFatal("ActiveMQ launched multiple threads...\n");
		foreach my $info (@ActiveMQList) {
			chomp $info;
			printFatal("$info\n");
		}
			fatalExit();
	}
}





##########################################################################################
#
#	PostgreSQL FUNCTIONS
#
##########################################################################################
sub stopPostgreSQL {
	system "sudo systemctl stop postgresql-12";
}


sub runPostgreSQL {
	printInfo("Starting PostgreSQL...\n"	);
	system "sudo systemctl start postgresql-12";
	sleep(2);
	if (system("sudo systemctl status postgresql-12") != 0) {
		printFatal("PostgreSQL did not respond to the startup request.\n");
		fatalExit();
	}
}


sub cleanPostgreSQL {
	printInfo("Cleaning out the PostgreSQL schema.\n");
	runPostgreSQL();
	printInfo("Dropping the schema.\n");
	system "sudo --login --user postgres psql --dbname mpf --command 'DROP OWNED BY mpf CASCADE'"
}



##########################################################################################
#
#	Redis FUNCTIONS
#
##########################################################################################
sub stopRedis {
	my @RedisList = ();

	printInfo("Checking for current instances of Redis...\n");

	#	If Redis is running tell it to shut down.
	@RedisList = `ps ax | grep \"redis-server\" | grep -v \"grep\"`;
	if((@RedisList) > 0) {
		printInfo("Redis is currently running.  Shutting it down.\n");
		system "redis-cli shutdown > /dev/null";
	}

	#	If Redis is still running, use the kill command to terminate it.\n";
	@RedisList = `ps ax | grep \"redis-server\" | grep -v \"grep\"`;
	if((@RedisList) > 0) {
		printError("Redis did not respond to the shutdown request.\n");
		foreach my $pid (@RedisList) {
			$pid =~ s/^\s+|\s+$//g;
			my @tokens = split /\s/, $pid;
			$pid = $tokens[0];
			printError("Killing Redis pid $pid.\n");
			system "sudo kill -9 $pid";
		}
	}

	#	If Redis is still running, then somethine went wrong.  Exit.
	@RedisList = `ps ax | grep \"redis-server\" | grep -v \"grep\"`;
	if((@RedisList) > 0) {
		printFatal("Redis did not respond to the shutdown request.\n");
		fatalExit();
	} else {
		printInfo("\t\tCurrently shutdown.\n");
	}
}


sub runRedis {
	my @RedisList = ();

	printInfo("Starting Redis...\n"	);
	system "sudo redis-server /etc/redis.conf > /dev/null";
	sleep(5);

	#Verify that Redis is running...
	@RedisList = `ps ax | grep \"redis-server\" | grep -v \"grep\" | grep -v \"Ds\"`;
	if((@RedisList) == 0) {
		printFatal("Redis did not respond to the startup request.\n");
		fatalExit();
	}
	if((@RedisList) > 1) {
		printFatal("Redis launched multiple threads...\n");
		foreach my $info (@RedisList) {
			chomp $info;
			printFatal("$info\n");
		}
			fatalExit();
	}
}


sub cleanRedis {
	printInfo("Cleaning out Redis.\n");
	runRedis();
	printInfo("Dropping the schema.\n");
	system "redis-cli flushall";
	stopRedis();
}




##########################################################################################
#
#	MAVEN FUNCTIONS
#
##########################################################################################
sub cleanMaven {
	if ((@_) != 1) {
		printFatal("Needed 1 argument: mpfPath.\n");
		fatalExit();
	}
	my $mpfPath = $_[0];

	my $pwd = `pwd`;

	printInfo("Cleaning MPF...\n");
	printWarn("\t\tThis may take a few minutes.\n");
	printWarn("\t\tTake a stretch break.\n");

	chdir "$mpfPath";
	open PIPE, "mvn clean -Pjenkins |";
	while(<PIPE>) {
		printMaven($_);
	}
	close PIPE;

	printInfo("\t\tRemoving the install directory\n");
	system "sudo rm -rf $mpfPath/trunk/install";

	printInfo("\t\tCleaning completed.\n");
	chdir $pwd;
}


sub mavenCompileNodeManager {
	if ((@_) != 1) {
		printFatal("Needed 1 argument: mpfPath.\n");
		fatalExit();
	}
	my $mpfPath = $_[0];
	my $pwd = `pwd`;

	printInfo("Compiling MPF Node Manager\n");

	chdir "$mpfPath/trunk/mpf-install";
	open PIPE, "mvn install -Pjenkins -DskipTests -Dmaven.test.skip=true -DskipITs -f node-manager-only-pom.xml |";
	while(<PIPE>) {
	   printMaven($_);
	}
	close PIPE;

	printInfo("Node Manager compilation completed.\n");

	chdir $pwd;
}




##########################################################################################
#
#	NODE MANAGER FUNCTIONS
#
##########################################################################################
sub stopNodeManager {
	my @NodeManagerList = ();

	printInfo("Checking for current instances of any node manager...\n");
	@NodeManagerList = `ps ax | grep $nodeManagerName | grep -v \"grep\"`;
	if((@NodeManagerList) > 0) {
		printInfo("Node managers are currently running.  Shutting them down.\n");
		system "sudo systemctl stop node-manager";
	}
	sleep(2);

	#	If Node manager is still running, use the kill command to terminate it.\n";
	@NodeManagerList = `ps ax | grep $nodeManagerName | grep -v \"grep\"`;
	if((@NodeManagerList) > 0) {
		printWarn("Node managers are currently running.  Shutting them down.\n");
		foreach my $pid (@NodeManagerList) {
			$pid =~ s/^\s+|\s+$//g;
			#print $pid."\n\n";
			my @tokens = split /\s/, $pid;
			$pid = $tokens[0];
			printWarn("Killing node-manager pid $pid.\n");
			system "sudo kill -9 $pid";
		}
	}

	#	If Node manager is still running, then somethine went wrong.  Exit.
	@NodeManagerList = `ps ax | grep $nodeManagerName | grep -v \"grep\"`;
	if((@NodeManagerList) > 0) {
		printFatal("Node manager did not respond to the shutdown request.\n");
		fatalExit();
	} else {
		printInfo("\t\tNone found\n");
	}
}


sub runNodeManager {
	my @NodeManagerList = ();


	printInfo("Starting node manager\n");
	system "sudo systemctl daemon-reload";
	my $ret = system "sudo systemctl start node-manager";


	printInfo("The return code for Node Manager is: $ret\n");
	if ($ret != 0) {
		printFatal("Node Manager failed to launch properly.  Killing build.\n");
		fatalExit();
	}

	#	Verify that node manager is running...
	@NodeManagerList = `ps ax | grep $nodeManagerName | grep -v \"grep\"`;
	if((@NodeManagerList) == 0) {
		printFatal("Node manager did not respond to the startup request.\n");
		fatalExit();
	}
	if((@NodeManagerList) > 1) {
		printFatal("Node manager launched multiple threads...\n");
		foreach my $info (@NodeManagerList) {
			chomp $info;
			printFatal("$info\n");
		}
		fatalExit();
	}
}


sub installNodeManager {
	if ((@_) != 1) {
		printFatal("Needed 1 argument: mpfPath.\n");
		fatalExit();
	}
	my $mpfPath = $_[0];
	my $pwd = `pwd`;
	chdir "$mpfPath";

	printInfo("Changing the permissions on the $mpfPath/trunk/install directory\n");
	system "sudo chmod -R 777 $mpfPath/trunk/install";

	if (-f "/etc/init.d/node-manager") {
		printInfo("Removing the old node manager installed in /etc/init.d\n");
		system "sudo rm /etc/init.d/node-manager";
	} else {
		printInfo("No old node-manager to remove.\n");
	}

	if (-f "$mpfPath/trunk/install/libexec/node-manager") {
		printInfo("Copying the node-manager script into /etc/init.d\n");
		system "sudo cp $mpfPath/trunk/install/libexec/node-manager /etc/init.d/";
	} else {
		printFatal("Node manager not in install/libexec.  Exiting\n");
		fatalExit();
	}

	if (!(-f "/etc/init.d/node-manager")) {
		printFatal("Node manager was not able to be copied to /etc/init.d\n");
		fatalExit();
	}

	chdir $pwd;
}





##########################################################################################
#
#	MISC FUNCTIONS
#
##########################################################################################

sub getSystemStatus {
	printInfo("Checking the known environment variables\n");
	my @vars = ("MPF_USER", "MPF_HOME", "MPF_LOG_PATH", "MASTER_MPF_NODE", "THIS_MPF_NODE", "CORE_MPF_NODES", "JAVA_HOME",
	    "JGROUPS_TCP_ADDRESS", "JGROUPS_TCP_PORT", "JGROUPS_FILE_PING_LOCATION", "ACTIVE_MQ_BROKER_URI", "LD_LIBRARY_PATH",
			 "ACTIVE_MQ_HOST", "REDIS_HOST");
	foreach my $var (@vars) {
		my $varInfo = `echo \$$var`;
		chomp $varInfo;
		printInfo("\t\t$var:\t$varInfo\n");
	}

	my @ActiveMQList = `ps ax | grep \"activemq\" | grep -v \"grep\" | grep -v \"perl\"`;
	if((@ActiveMQList) == 0) {
		printWarn("ActiveMQ is not currently running.\n");
	} elsif((@ActiveMQList) == 1) {
		printInfo("ActiveMQ is currently running.\n");
	} else {
		printError("ActiveMQ is currently running ".(@ActiveMQList)." instances.\n");
	}

	if (system("sudo systemctl status postgresql-12") == 0) {
		printInfo("PostgreSQL is currently running.\n");
	}
	else {
		printWarn("PostgreSQL is not currently running.\n");
	}

	my @RedisList = `ps ax | grep \"redis-server\" | grep -v \"grep\"`;
	if((@RedisList) == 0) {
		printWarn("Redis is not currently running.\n");
	} elsif((@RedisList) == 1) {
		printInfo("Redis is currently running.\n");
	} else {
		printError("Redis is currently running ".(@RedisList)." instances.\n");
	}

	my @NodeManagerList = `ps ax | grep $nodeManagerName | grep -v \"grep\" | grep -v \"perl\"`;
	if((@NodeManagerList) == 0) {
		printWarn("Node Manager is not currently running.\n");
	} elsif((@NodeManagerList) == 1) {
		printInfo("Node Manager is currently running.\n");
	} else {
		printError("Node Manager is currently running ".(@NodeManagerList)." instances.\n");
	}

	my @nodesToCheck = ("amq_detection_component", "mpf-speech-detection");
	foreach my $nodeToCheck (@nodesToCheck) {
		my @NodesList = `ps ax | grep \"$nodeToCheck\" | grep -v \"grep\" | grep -v \"perl\"`;
		if((@NodeManagerList) == 0) {
			printWarn("Node $nodeToCheck is not currently running.\n");
		} else {
			printInfo("Node $nodeToCheck is currently running ".(@NodesList)." instances.\n");
		}
	}
}

sub killEverythingElse {
	my @nodesToCheck = ("amq", "main", "surefire", "tomcat", "speech");
	foreach my $nodeToCheck (@nodesToCheck) {
		my @NodesList = `ps ax | grep \"$nodeToCheck\" | grep -v \"grep\" | grep -v \"perl\"`;
		if((@NodesList) != 0) {
			printInfo("Node $nodeToCheck is currently running ".(@NodesList)." instances.\n");
			foreach my $pid (@NodesList) {
				$pid =~ s/^\s+|\s+$//g;
				my @tokens = split /\s/, $pid;
				$pid = $tokens[0];
				printError("Killing pid $pid.\n");
				system "sudo kill -9 $pid";
			}
		}
	}
}

sub cleanTmp {
	if ((@_) != 1) {
		printFatal("Needed 1 argument: mpfLogPath.\n");
		fatalExit();
	}
	my $mpfLogPath = $_[0];
	my @files = ();

	printInfo("Removing UUID-based files...\n");
	@files = </tmp/*-*-*-*-*.*>;
	foreach my $file (@files) {
		system "sudo rm -rf $file";
	}

	printInfo("Removing XML files...\n");
	@files = </tmp/*.xml>;
	foreach my $file (@files) {
		system "sudo rm $file";
	}

	printInfo("Removing video files...\n");
	@files = </tmp/*.avi>;
	foreach my $file (@files) {
		system "sudo rm $file";
	}

	printInfo("Removing image files...\n");
	@files = </tmp/*.png>;
	foreach my $file (@files) {
		system "sudo rm $file";
	}

	printInfo("Removing log files...\n");
	@files = <$mpfLogPath/trunk/install/log/*>;
	foreach my $file (@files) {
		system "sudo rm -rf $file";
	}
}

sub runGTests {
	if ((@_) != 1) {
		printFatal("Needed 1 argument: mpfPath.\n");
		fatalExit();
	}
	my $mpfPath = $_[0];

	my $pwd = `pwd`;
	my $rc = 0;

    my $detectionPath = File::Spec->catfile($mpfPath,'mpf-component-build');

    my @gtestPaths = File::Find::Rule->directory->name('test')->in($detectionPath);
    # printDebug("gtestPaths:\n", join("\n", @gtestPaths), "\n");

    my $trunkBuildPath = File::Spec->catfile($mpfPath,'openmpf/trunk/build');
    push(@gtestPaths, File::Find::Rule->directory->name('test')->in($trunkBuildPath));
    # printDebug("gtestPaths:\n", join("\n", @gtestPaths), "\n");

    my @tests = File::Find::Rule->file->executable->name('*Test')->in(@gtestPaths);
    # printDebug("tests:\n", join("\n", @tests), "\n");

    foreach my $test (@tests) {
        chdir(File::Basename::dirname($test));
        printInfo("Beginning gtest $test.\n");
        my $rcTemp = system "$test --gtest_output='xml:$test.junit.xml'";
        if ($rcTemp != 0) {
            $rc = $rcTemp;
        }
    }

# TODO: Reinvestigate utility of cobertura
#	system "mkdir -p $mpfPath/mpf_components/CPP/detection/target/site/cobertura";
#	if (!(-d "$mpfPath/mpf_components/CPP/detection/target/site/cobertura")) {
#		printFatal("The cobertura directory ($mpfPath/trunk/detection/target/site/cobertura) was not found.\n");
#		fatalExit();
#	}
#	printInfo("Running gcovr...\n");
#	my $gcovrRetCode = system "gcovr -v --xml -r $mpfPath/mpf_components/CPP -o $mpfPath/mpf_components/CPP/detection/target/site/cobertura/coverage.xml";
#	printInfo("The gcover return code was $gcovrRetCode.\n");
#
#	if (!(-f "$mpfPath/mpf_components/CPP/detection/target/site/cobertura/coverage.xml")) {
#		printError("The cobertura file ($mpfPath/mpf_components/CPP/detection/target/site/cobertura/coverage.xml) was not successfully created.\n");
#	}

	if ($rc != 0) {
		printError("GTESTS TESTS FAILED!");
		printError("----\t\tThis will mark the build as UNSTABLE.\t\t----\n");
	} else {
		printDebug("GTests run successfully.\n");
	}

	chdir $pwd;
}


sub installProfile {
	if ((@_) != 3) {
		printFatal("Needed 3 arguments: mpfPath mpfLogPath hostname.\n");
		fatalExit();
	}
	my $mpfPath	= $_[0];
	my $mpfLogPath	= $_[1];
	my $hostname	= $_[2];
	my $pwd = `pwd`;
	chdir "$mpfPath";

	my $mpfUser = ($hostname eq "jenkins-mpf-1.mitre.org") ? "jenkins" : "jenkins-slave";
	printInfo("Using MPF_USER: $mpfUser\n");

    # NOTE: On Jenkins, the mpf.sh file is sourced before running the node-manager process, but not before running maven.
    # Any environment variables set below should also be set through one of two Jenkins UIs:
    # - Global env. vars: http://jenkins-mpf-1.mitre.org:8080/configure
    # - Host-specific env. vars: http://jenkins-mpf-1.mitre.org:8080/computer/(master)/configure,
    #                            http://jenkins-mpf-1.mitre.org:8080/computer/jenkins-mpf-2.mitre.org/configure,
    #                            http://jenkins-mpf-1.mitre.org:8080/computer/jenkins-mpf-3.mitre.org/configure, etc.

	my $mpfsh = << "END_MPF_SH";
export MPF_USER="$mpfUser"
export MPF_HOME="$mpfPath/trunk/install"
export MPF_LOG_PATH="$mpfLogPath"
export MASTER_MPF_NODE="$hostname"
export THIS_MPF_NODE="$hostname"
export CORE_MPF_NODES="\$THIS_MPF_NODE"
export JAVA_HOME="/etc/alternatives/java_sdk_11"
export JGROUPS_TCP_ADDRESS="\$THIS_MPF_NODE"
export JGROUPS_TCP_PORT=7800
export JGROUPS_FILE_PING_LOCATION="\$MPF_HOME/share/nodes"
# CATALINA_OPTS is set in <TOMCAT_HOME>/bin/setenv.sh
export ACTIVE_MQ_HOST="\$MASTER_MPF_NODE"
export REDIS_HOST="localhost"
export ACTIVE_MQ_BROKER_URI="failover://(tcp://\$ACTIVE_MQ_HOST:61616)?jms.prefetchPolicy.all=0&startupMaxReconnectAttempts=1"
export LD_LIBRARY_PATH="/usr/local/lib"
END_MPF_SH


	if (-f "/etc/profile.d/mpf.sh") {
		printInfo("Removing the old profile installed in /etc/profile.d\n");
		system "sudo rm /etc/profile.d/mpf.sh";
	} else {
		printInfo("No old profile to remove.\n");
	}

	if (-f "/etc/profile.d/mpf.sh") {
		printFatal("Failed to remove the old profile installed in /etc/profile.d\n");
		fatalExit();
	}

	system "sudo touch /etc/profile.d/mpf.sh";
	system "sudo chmod 777 /etc/profile.d/mpf.sh";

	open FILE, ">", "/etc/profile.d/mpf.sh";
	print FILE $mpfsh;
	close FILE;

	if (!(-f "/etc/profile.d/mpf.sh")) {
		printFatal("Profile was not able to be copied to /etc/profile.d\n");
		fatalExit();
	} else {
		printInfo("Profile successfully copied to /etc/profile.d\n");
	}

	chdir $pwd;
}



##########################################################################################
#
#	PRINT FUNCTIONS
#
##########################################################################################
sub printDebug {
	if ($loggingLevel <= $DEBUG) {
		print STDOUT Term::ANSIColor::color("blue");
		print STDOUT "[DEBUG] ";
		print STDOUT @_;
		print STDOUT Term::ANSIColor::color("reset");
	}
}

sub printDebugNoTag {
	if ($loggingLevel <= $DEBUG) {
		print STDOUT Term::ANSIColor::color("blue");
		print STDOUT @_;
		print STDOUT Term::ANSIColor::color("reset");
	}
}

sub printInfo {
	if ($loggingLevel <= $INFO) {
		print STDOUT Term::ANSIColor::color("green");
		print STDOUT "[INFO] ";
		print STDOUT @_;
		print STDOUT Term::ANSIColor::color("reset");
	}
}

sub printInfoNoTag {
	if ($loggingLevel <= $INFO) {
		print STDOUT Term::ANSIColor::color("green");
		print STDOUT @_;
		print STDOUT Term::ANSIColor::color("reset");
	}
}

sub printWarn {
	if ($loggingLevel <= $WARN) {
		print STDOUT Term::ANSIColor::color("yellow");
		print STDOUT "[WARN] ";
		print STDOUT @_;
		print STDOUT Term::ANSIColor::color("reset");
	}
}

sub printWarnNoTag {
	if ($loggingLevel <= $WARN) {
		print STDOUT Term::ANSIColor::color("yellow");
		print STDOUT @_;
		print STDOUT Term::ANSIColor::color("reset");
	}
}

sub printError {
	if ($loggingLevel <= $ERROR) {
		print STDERR Term::ANSIColor::color("red");
		print STDERR "[ERROR] ";
		print STDERR @_;
		print STDERR Term::ANSIColor::color("reset");
	}
}

sub printErrorNoTag {
	if ($loggingLevel <= $ERROR) {
		print STDERR Term::ANSIColor::color("red");
		print STDERR @_;
		print STDERR Term::ANSIColor::color("reset");
	}
}

sub printFatal {
	if ($loggingLevel <= $FATAL) {
		print STDERR Term::ANSIColor::color("magenta");
		print STDERR "[FATAL] ";
		print STDERR @_;
		print STDERR Term::ANSIColor::color("reset");
	}
}

sub printFatalNoTag {
	if ($loggingLevel <= $FATAL) {
		print STDERR Term::ANSIColor::color("magenta");
		print STDERR @_;
		print STDERR Term::ANSIColor::color("reset");
	}
}

sub fatalExit {
	printFatal("There was an unrecoverable error.  Exiting.\n");
	exit -1;
}

sub printMaven {
	foreach my $output (@_) {
		if ($output =~ /\[ERROR\]/) {
			printErrorNoTag($output);
		} elsif ($output =~ /\[WARNING\]/) {
			printWarnNoTag($output);
		} elsif ($output =~ /\[INFO\]/) {
			printInfoNoTag($output);
		} elsif ($output =~ /\[DEBUG\]/) {
			printDebugNoTag($output);
		} elsif ($output =~ /\[FATAL\]/) {
			printFatalNoTag($output);
		} elsif ($output =~ /\[.?.?.?.?\]/) {
			printDebugNoTag($output);
		} else {
			print $output;
		}
	}
}
