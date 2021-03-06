# MEND 
(My ENcrypted Data) provides a simple interface for encryption of sensitive data.

MEND makes a deliberate distinction between "logs" and "files". After a small amount of setup, you can use MEND to encrypt and decrypt logs and files with simple and short commands. You only need to remember one password to unlock MEND which will then allow you to decrypt your logs and files.

## SETUP:

First build the code using the gradle wrapper. There is currently no automatic installation process, but there are some useful scripts you can add to your system path under desktop-mend/scripts. You will need to change the path to MEND4.jar in the script though. 

Next open a terminal and run:

	mend setup

MEND will ask you to input a password, and then create a "Settings.xml" file in the directory ~/.MEND4 (under your users home directory). This file is very important as it contains your generated RSA keys (which can NOT be re-generated from the same password, so consider keeping a backup.) When you encrypt a log/file, your public RSA key will be used to encrypt an AES key that is used to encrypt your log/file. The public key is stored as is, however your private key is stored encrypted and requires your password to decrypt. Your "Settings.xml" file also contains a bunch of other things that you will need to set before using MEND. When interacting with your Settings.xml file, it is best to use the commands:

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


### WARNING! YOUR SHRED COMMAND WILL BE RUN ON EVERY FILE IN DECDIR WHEN YOU RUN:
        
	mend clean

It is a bad idea to leave anything in decdir that you wish to keep. The shredcommand property tells mend how to destroy decrypted files by storing a command to be run per file to be destroyed. You can use what ever file shredding/deleting utility you like as long as you substitute the position of the filename for the term "<filename>". For example:

	mend set shredcommand "shred -u <filename>"
	
or (a less secure version):

	mend set shredcommand "rm <filename>"
	

## LOCKING AND UNLOCKING:

In order to decrypt anything that has been encrypted by MEND, you will first need to unlock MEND using the command:

	mend unlock

MEND will ask you for your password, and then decrypt your private key and store it in a file called "prKey" next to your "Settings.xml" file. It will also store your public key in a file called pubKey. If you wish to change your password, but keep your keys, you can do so by calling:

        mend setup <private key file> <public key file>

It is important to run remember to run:

	mend lock	

when you're done decrypting and viewing information. This command will use the "shredcommand" as set up earlier to destroy your decrypted private and public key files.


## ENCRYPTING

To encrypt text to your currentlog use

        mend enc
	
By default this will open up a text entry box where you can type/paste your text. Use Ctrl+Enter to commit the text and clear the text box, or Shift+Enter to commit the text and close the text box. Each log is appended to the current log file under a header marking the date/time and the version of MEND used to commit that log.

To encrypt a file use:

        mend enc <file> [<custom file name>]

In the absence of a custom filename, MEND will print out the id of the encrypted file, which you can later use to decrypt that file. The encrypted file is stored in your "encdir" as set up earlier.

You can also use:

        mend enc -a

To append to the current log without a header.

        mend enc -d <text>

To encrypt text straight from the command line into the log. Or

        mend enci

To encrypt text from standard input (until an EOF is met).


## DECRYPTING

To decrypt a log, first unlock MEND, and then run:

        mend dec <name of log>
	
You don't need to specify the full path to the log file, MEND will look first to see if the name represents an absolute path, but it will default to looking for that log file in your "logdir". You also don't need to use the suffix ".mend".

To decrypt a file, use:

        mend dec <file id> | <path to encrypted file>
	
MEND will decrypt the file to your "decdir". By default, mend will use your systems default program for that file type to open the file once it is decrypted. You can supress this behaviour using:

        mend dec -s <file id> | <path to encrypted file>
	

## CLEANING

When you are done viewing your encrypted logs/files, you can run:

       mend clean
       
This will run your "shredcommand" on every file in your "decdir". Once again, don't put anything in this folder you wish to keep. 


## MERGING

You can merge two logs together into one log file using the merge subcommand. MEND needs to be unlocked to do this because it will read the date/time at the top of each log and order the output log by date. e.g.

        mend merge in1.mend in2.mend out.mend

Another way to use this command is with the -1 or -2 flag (instead of providing an output log file name.) This way the output log will replace the the first or second logs respectively. e.g.

        mend merge -1 in1.mend in2.mend


## HELP

If you need reminding of any of the basics written above, you can get help using:

        mend -h

or

        mend <subcommand> -h
