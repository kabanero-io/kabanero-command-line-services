  
 For the POSTMAN collection to work you'll need to set the environment variables in POSTMAN:

1. settings (gear icon on the upper right) --> add: myenv
2. add variables for myurl, user and gitpat, and finally JWT:
   a. myurl: points to your cli service URL ( you can get this from the landing page )
   b. user: is your GIT hub userid
   c. gitpat: is either your GIT password or GIT personal access token
   d. JWT: is the json web token that you get back isse the POST remote auth call
