# Kabanero Command Line Service Troubleshooting Guide

## Kabanero CLI cannot connect to Kabanero Command Line Service endpoint

1. Validate endpoint
    
    * Log into the OKD Console
    	* Select Application Console from the dropdown
    	* Select `Kabanero` from the list of projects
    	* Go to Applications->Routes
    	* Verify that the route for CLI matches the endpoint you are using for login
   
1.  Check CLI Pod
   
    * Log into the OKD Console
    	* Select Application Console from the dropdown
    	* Select `Kabanero` from the list of projects
    	* Go to Applications->Pods
    	* Select the `kabanero-cli` pod, make sure it's in `Running` status
    	* Click on the logs tab, examine log for any exceptions
    	
## Unexpected output or exceptions from LIST or REFRESH commands
 
1.  Check CLI Pod
   
    * Log into the OKD Console
    	* Select Application Console from the dropdown
    	* Select `Kabanero` from the list of projects
    	* Go to Applications->Pods
    	* Select the `kabanero-cli` pod, make sure it's in `Running` status
    	* Click on the logs tab, examine log for any exceptions
 

   
