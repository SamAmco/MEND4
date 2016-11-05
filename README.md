MEND (My ENcrypted Data) aims to provide a simple command line interface for encryption of sensitive data on your local machine.

MEND makes a deliberate distinction between "logs" and "files". You can append text to an encrypted log (which is useful for: automating encrypted logging, storing passwords or keeping an encrypted diary.) If you use MEND to encrypt a file, it will store the file with a 17 digit date/time ID, which can then be used to select it for decryption.

After a small amount of setup, you can use MEND to encrypt and decrypt logs and files with simple and short commands. You only need to remember one password to unlock MEND which will then allow you to decrypt your logs and files.


SETUP:

First build the code using "ant clean jar" and move the jar and batch/shell script to a location on your machine recognised by your system path. There is no difference between the jar file placed under /Windows or /Unix, this is just for convenience when copying the script and jar together.

Next open a terminal and run:

	mend setup

MEND will ask you to input a password, and then create a "Settings.xml" file in the directory ~/.MEND4 (under your users home directory). This file is very important as it contains your generated RSA keys (which can NOT be re-generated from the same password, so consider keeping a backup.) When you encrypt a log/file, your public RSA key will be used to encrypt an AES key that is used to encrypt your log/file. The public key is stored as is, however your private key is stored encrypted (using a hash of your password as a key.) Your "Settings.xml" file also contains a bunch of other things that you will need to set before using MEND. When interacting with your Settings.xml file, it is best to use the commands:

	mend get [-a | -l | -e] | <property>

to get properties in your "Settings.xml" file and:

	mend set <property name> <value>

to set properties.

To complete your setup you'll need to set the following properties:

	mend set currentlog <log file name>
	
	mend set logdir <directory to store logs>
	
	mend set encdir <directory to store encrypted files>
	
	mend set decdir <directory to store decrypted files>
	
	mend set shredcommand <shred command>
	
The shredcommand property tells mend how to destroy decrypted files by storing a command to be run per file to be destroyed. You can use what ever file shredding/deleting utility you like as long as you substitute the position of the filename for the term "<filename>". For example:

	mend set shredcommand "shred -u <filename>"
	
or (a less secure version):

	mend set shredcommand "rm <filename>"
	

(This section is a work in progress)	
USAGE:

In order to decrypt anything that has been encrypted by MEND, you will first need to unlock MEND using the command:

	mend unlock

MEND will ask you for your password, before hashing it and using that hash to decrypt your private key and store it in a file called "prKey.dec" next to your "Settings.xml" file. It is important to run remember to run:

	mend lock	

when you're done decrypting and viewing information. This command will use the "shredcommand" as set up earlier to destroy your decrypted private key file.
