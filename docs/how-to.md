# Skadi Gist - How To
First you need to be aware by creating a gist you are uploading not only a screenshot but also the underlying AST. The underlying AST contains information like language and concept names, all property values of nodes in the AST, model names of reference targets outside the AST and node ids. For public gists these information are afterwards accessible to everyone browsing gists on the website. If you are not comfortable with uploading these information do not create a gist. 
## Installing the Plugin 
The preferred method of installing the [MPS plugin](https://plugins.jetbrains.com/plugin/17752-skadi-cloud--gist) is the JetBrains Plugin Marketplace. You will get updates automatically if you are installing the plugin from it. 
If you don’t have access to the marketplace you can install it from the [Github release page](https://github.com/skadi-cloud/gist/releases).
## Creating Gists 
### Log In (Optional) 
Logging into skadi gist is totally optional for the core functionality of sharing code snippets. But some features like privates gists aren’t available without. If you don’t login the gists won’t show up on your user page on the website and are listed as “anonymous” user. You also can’t delete a gist yourself after creating it if you aren’t logged in. 
To login open the MPS preferences and navigate to `Tools -> Skadi Cloud` :

![](how-to/CleanShot%202021-10-10%20at%2019.14.58.png)

Click the “Login” link which will take you to the gist.skadi.cloud and will ask you to sign in with Github. Afterwards the dialog will show your Github username if the login was successful:

![](how-to/CleanShot%202021-10-10%20at%2019.18.12.png)

### Selecting a Node
You can start creating gist a fort a something directly form the editor, richt click on the node you want to share and select `Create Gist`:

![](how-to/CleanShot%202021-10-10%20at%2018.40.35.png)

Note: *For some languages it might happen that you start creating the gist from an undesired node. For instance in base language the visibility of a member is a separate node. If you create gist of that you will only share e.g. the keyword public.*
If you like to share a one or multiple root nodes you can do so from the logical view by marking the root nodes and selecting `Create Gist` from the context menu:

![](how-to/CleanShot%202021-10-10%20at%2018.53.09.png)

### Submitting the Gist 
After you selected the node the “Skadi Cloud” tool window will open:

![](how-to/CleanShot%202021-10-10%20at%2018.57.50.png)

Here you can fill in additional fields. 
*Title*: A one line title for the gist, shown on the website. If left blank a name is automatically chosen. 
*Description*: A more extensive description of the content of the gist. For instance describing what the code in the gist does or why it is written the way it is. For formatting markdown is supported similar to comments on Github no embedded HTML is supported. 
*Visibility*: 
- Public - Listed on the front page. 
- Unlisted - Unlisted on the front page but accessible for everybody with the link to it. 
- Private - Only visibility to you if you are logged in on the website. Requires to be logged in MPS when creating the gist. 
Clicking `Create gist` will start creating the gist in the background. Once the gist creation is completed you will get a notification in the IDE:

![](how-to/CleanShot%202021-10-10%20at%2019.20.57.png)

You can view the gist in the browser or directly copy the URL for easy sharing. 
## Importing a Gist 
For importing a gist, you need the address of it. You can either copy a URL that is shared with you or copy it from the browser by clicking the “Copy Link” button on the website. 
Once you have copied the URL you can import it into MPS via `Code -> Import Gist`, this action is only enabled if a valid link to a gist is detected in the clipboard. 
After selecting `Import Gist` you either get a notification that the content of the gist has been placed into your clipboard and you can paste it like any other code. If the gist contains one or more root nodes you are presented a dialog to select the model where the gist shall be imported:

![](how-to/CleanShot%202021-10-10%20at%2019.38.47.png)

After selecting a model the nodes are imported. 

