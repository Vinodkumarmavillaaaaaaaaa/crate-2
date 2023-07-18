# Licensed to Crate.io GmbH ("Crate") under one or more contributor
# license agreements.  See the NOTICE file distributed with this work for
# additional information regarding copyright ownership.  Crate licenses
# this file to you under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.  You may
# obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
# License for the specific language governing permissions and limitations
# under the License.
#
# However, if you have executed another commercial license agreement
# with Crate these terms will supersede the license and you may use the
# software solely pursuant to the terms of the relevant commercial agreement.

from crate.theme.rtd.conf.crate_reference import *

exclude_patterns = ['out/**', 'tmp/**', 'eggs/**', 'requirements.txt', 'README.rst']

extensions.append('crate.sphinx.csv')

linkcheck_ignore = [
    'https://www.iso.org/obp/ui/.*'  # Breaks accessibility via JS ¯\_(ツ)_/¯
]
linkcheck_retries = 3


--------------------------------------------------------------------------------------------------------------------------------------------------------------------

This code belongs to test team.
pipeline {
   agent { label 'cicada-automation-node' }  
   //environment {
   //     path ='/home/centos/workspace/CICADA/Regression/Cluster-Health-Status-Dev'
   //    
   //}
   parameters {
            string defaultValue: '1.2.3.4', description: 'Cluster primary master ip', name: 'MASTER_IP', trim: false
            string defaultValue: 'centos', description: 'Primary master login user name', name: 'USERNAME', trim: false
            string defaultValue: 'cicada', description: 'Primary master login password', name: 'PASSWORD', trim: false
            string defaultValue: 'master', description: 'Branch Name', name: 'BRANCH', trim: false
            text defaultValue: '', description: 'Private key', name: 'PKEY', trim: false
        }
   stages {
        
       stage('git checkout of cicada repo') {
            steps {
	    	 	   
	    	 	   checkout([$class: 'GitSCM', branches: [[name: "${BRANCH}"]], extensions: [[
                      $class: 'RelativeTargetDirectory',
                            relativeTargetDir: "automation"]], 
                            userRemoteConfigs: [[credentialsId: '00313e2a-6b4b-4bbe-bbc3-9fdc84b98ee9', url: 'https://bitbucket.fnc.fujitsu.com/scm/cicfwk/automation.git']]])
            }
        } 
	     stage('Install Modules') {
            steps {
                script{
                    
                    if ( PKEY){
                        sh """
                            ls -la 
                            echo "${PKEY}" > automation/keyfile.pem
                            chmod 600 automation/keyfile.pem
                            sshpass ssh -i automation/keyfile.pem "${MASTER_IP}" -l "${USERNAME}" -o StrictHostKeyChecking=no "rm -rf /tmp/robot;mkdir -p /tmp/robot" 
                            sshpass scp -i automation/keyfile.pem  -r automation ${USERNAME}@${MASTER_IP}:/tmp/robot/
                            sshpass ssh -i automation/keyfile.pem "${master_ip}" -l "${USERNAME}" -o StrictHostKeyChecking=no  "source py3/bin/activate;cd /tmp/robot/automation; python3 -m pip install -r requirements.txt"
                        """
                        
                    }
                    else if(PASSWORD){
                        sh """
                            sshpass -p "${PASSWORD}" ssh  "${PRIMARY_MASTER_IP}" -l "${USERNAME}" -o StrictHostKeyChecking=no "rm -rf /tmp/robot;mkdir -p /tmp/robot"
                            sshpass -p "${PASSWORD}" scp -r automation ${USERNAME}@${PRIMARY_MASTER_IP}:/tmp/robot/
                            sshpass -p "${PASSWORD}" ssh  "${master_ip}" -l "${USERNAME}" -o StrictHostKeyChecking=no "source py3/bin/activate;cd /tmp/robot/automation; python3 -m pip install -r requirements.txt"
                        """
                        
                    }
                }
            }
            
        }
       stage('running robot test cases'){
           steps{
                script{
                    
                    if ( PKEY){
                        sh '''
                        sshpass ssh -i automation/keyfile.pem "${MASTER_IP}" -l "${USERNAME}" -o StrictHostKeyChecking=no "export PATH=${path}:/var/lib/rancher/rke2/bin/;export PYTHONPATH=/tmp/robot/automation;source py3/bin/activate; cd /tmp/robot/automation; python3 -m robot tests/system/rke2/rke2-installer.robot" 
                        '''
                        
                    }
                    else if(PASSWORD){
                        sh '''
                        sshpass -p "${PASSWORD}" ssh  "${PRIMARY_MASTER_IP}" -l "${USERNAME}" -o StrictHostKeyChecking=no "export PATH=$PATH:/var/lib/rancher/rke2/bin/;export PYTHONPATH=$(pwd);source py3/bin/activate; cd /tmp/robot/automation; python3 -m robot tests/system/rke2/rke2-installer.robot" 
                        '''
                        
                    }
                }
            }
           
        }
    }
   post {
        
        always{
            
            script{
                if (PKEY){
                    
                    sh """
                       
                        sshpass scp -i automation/keyfile.pem ${USERNAME}@${MASTER_IP}:/tmp/robot/automation/*html .
                        
                    """
                    
                } 
                else{
                        sh """
                            sshpass -p "${PASSWORD}" scp ${USERNAME}@${MASTER_IP}:/tmp/robot/automation/*html .
                        """
                }
                
            }
            
            archiveArtifacts allowEmptyArchive: true, artifacts: '*html', followSymlinks: false
        }
    }
}

It is only for dev, QA and Test team 
