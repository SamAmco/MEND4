Read the [project README](../README.md) first if you haven't already for general concepts.

# Installation

To get started you will need to build or download the mend.jar file (see [Releases](https://github.com/SamAmco/MEND4/releases)). To run the jar you will need java installed. You can run the jar with: 

```
java -jar /path/to/mend.jar <arguments>
```

However I much prefer to store this next to a platform specific wrapping script called `mend` e.g.

```
#!/bin/sh

java -jar /path/to/mend.jar ${1+"$@"}
```

and add that to my path. For the rest of this setup I will assume you have done this.

# Setup

First run:

	mend setup

MEND will ask you to input a password as well as a bunch of other parameters, and then create a `config.xml` file in the directory `~/.mend4` (under your users home directory). This file is very important as it contains your generated asymmetric keys (which can NOT be re-generated from the same password, so consider keeping a backup). I recommend you just use the default values for all of the parameters but for more details see the [setup paramaters](#setup-parameters) section.

In the setup output you will see MEND has chosen some default directories for you for `log-dir`, `enc-dir`, and `dec-dir`. These are the places where your `.mend`, `.enc`, and decrypted files will be stored respectively. You can change these using the `mend set` command. In fact you can change all of the parameters using the `mend set` command as everything is stored in your `config.xml` file. 

When interacting with your `config.xml` file, it is best to use the commands:

	mend get <property>

to get properties in your `config.xml` file and:

	mend set <property-name> <value>

to set properties.

There are also shorthand commands `mend gcl <log-file>` and `mend scl <log-file>` for getting and setting your current log file respectively.

One property you are advised to set manually is:
	
	mend set shred-command <shred-command>

### WARNING! YOUR SHRED COMMAND WILL BE RUN ON EVERY FILE IN DEC-DIR WHEN YOU RUN:
        
	mend clean

It is a bad idea to leave anything in dec-dir that you wish to keep. The `shred-command` property tells MEND how to destroy decrypted files and your private key file by storing a command to be run per file to be destroyed. You can use what ever file shredding/deleting utility you like as long as you substitute the position of the filename for the term `<filename>`. For example:

	mend set shred-command "shred -u <filename>"
	
or (a less secure version):

	mend set shred-command "rm <filename>"
	

## LOCKING AND UNLOCKING:

In order to decrypt anything that has been encrypted by MEND, you will first need to unlock MEND using the command:

	mend unlock

MEND will ask you for your password, and then decrypt your private key and store it in a file called `prKey` next to your `config.xml` file. (It will also store your public key in a file called pubKey). 

It is important to run remember to run:

	mend lock	

when you're done decrypting and viewing information. This command will use the `shred-command` as set up earlier to destroy your decrypted private key file.

If you used:

    mend dec <enc-file-id>

to decrypt a `.enc` file, it will have been decrypted to your `dec-dir`. In this case you should ALSO run: 

    mend clean

to securely delete all files in `dec-dir`


## ENCRYPTING

To encrypt text to your current-log use

    mend enc
	
By default this will open up a text entry box where you can type/paste your text. Use Ctrl+Enter to commit the text and clear the text box, or Shift+Enter to commit the text and close the text box. Each log is appended to the current log file under a header marking the date/time and the version of MEND used to commit that log.

To encrypt a file use:

    mend enc <file> [<custom-file-name>]

In the absence of a custom file name, MEND will print out the id of the encrypted file, which you can later use to decrypt that file. The encrypted file is stored in your `enc-dir` as set up earlier.

You can also use:

    mend enc -a

To append to the current log without a header.

    mend enc -d "<text>"

To encrypt text straight from the command line into the log. Or

    mend enci [-a]

To encrypt text from standard input (until an EOF is met).


## DECRYPTING

To decrypt a log, first unlock MEND, and then run:

    mend dec <name-of-log>
	
You don't need to specify the full path to the log file, MEND will look first to see if the name represents an absolute path, but it will default to looking for that log file in your `log-dir`. You also don't need to use the suffix `.mend`.

To decrypt a file, use:

    mend dec <file-id> | <path-to-encrypted-file>
	
MEND will decrypt the file to your `dec-dir`. By default, MEND will use your systems default program for that file type to open the file once it is decrypted. You can supress this behaviour using:

    mend dec -s <file-id> | <path-to-encrypted-file>
	

## CLEANING

When you are done viewing your encrypted logs/files, you can run:

    mend clean

This will run your `shred-command` on every file in your `dec-dir`. Once again, don't put anything in this folder you wish to keep. 


## MERGING

You can merge two logs together into one log file using the `merge` subcommand. MEND needs to be unlocked to do this because it will read the date/time at the top of each log and order the output log by date. e.g.

    mend merge in1.mend in2.mend out.mend

Another way to use this command is with the -1 or -2 flag (instead of providing an output log file name.) This way the output log will replace the the first or second logs respectively. e.g.

    mend merge -1 in1.mend in2.mend


## CHANGING PASSWORD

To change your password or password hashing parameters first unlock mend using: 

    mend unlock

Then run:

    mend change-password

You will be asked for a new password and password hashing parameters. Again you can use the defaults here or tune them to your machine. See [the setup parameters section](#setup-parameters) for more details.

Changing your password will re-encrypt your private key with your new password/password hashing parameters and overwrite the `encrypted-private-key` parameter in your `config.xml` file.

Don't forget to run: 

    mend lock

To lock mend again afterwards.

## HELP

If you need reminding of any of the basics written above, you can get help using:

    mend -h

or

    mend <subcommand> -h


## SETUP PARAMETERS

In the setup process you will have seen some text like this: 

```
It is recommended to use the defaults for these settings but you may use any asymmetric cipher provided by your JCA.

What asymmetric cipher would you like to use? (Default X448):

What asymmetric cipher transform would you like to use? (Default XIESWithSha256):

What asymmetric key size would you like to use? (Default 448):

MEND uses Argon2id version 13 to derive a key from your password. You can tune the following parameters to tradeoff unlock speed for security.

How many iterations would you like to use for the password key factory? (Default 3):

How many threads would you like to use for the password key factory? (Default 8):

How much memory in KB would you like to use for the password key factory? It must be at least double the parallelism (Default 256000):
```

The defaults are chosen because they are strong and tested, but you can change these parameters if you like. 

### Asymmetric cipher

The default asymmetric cipher used is ECIES in stream mode with the [goldilocks 448 curve](https://en.wikipedia.org/wiki/Curve448) which should provide 224 bits of security. The use of sream mode is ok here as we only ever encrypt randomly generated AES 256 bit keys with this cipher, never plain text and never the same key twice. The implementation is provided by the bouncy castle dependency (see [the core gradle build file](../core-mend/build.gradle) for the dependency co-ordinates)

The asymmetric cipher name, transform and key size are used to create a key pair and encrypt symmetric keys. The available parameters here are provided by the underlying JCA/JCE of your java distribution as well as by the bouncy castle library embedded in `mend.jar`. The following code roughly demonstrates how they are used in practice: 

```kotlin
val keyGen = KeyPairGenerator.getInstance(asymmetricCipherName)
keyGen.initialize(keySize)
val keyPair = keyGen.genKeyPair()

val cipher = Cipher.getInstance(asymmetricCipherTransform)

cipher.init(Cipher.ENCRYPT_MODE, keyPair.public)

val cipherText = cipher.doFinal(...)
```

Not all available values will actually work here as some ciphers require further parameters which MEND may not know how to provide. To see a list of all tested values check out [this test class](../core-mend/src/test/java/crypto/DefaultJCECryptoProviderTest.kt).

### Password hashing parameters

The [Argon2id algorithm](https://github.com/P-H-C/phc-winner-argon2) is a password hashing algorithm based on a [memory hard](https://en.wikipedia.org/wiki/Memory-hard_function) function. It was chosen as it is more resistant to brute force using hashing ASICS than other password hashing algorithms like PBKDF2 with Sha256. It was also the winner of the 2015 [password hashing competition](https://en.wikipedia.org/wiki/Password_Hashing_Competition). The three required parameters allow you to tune the tradeoff between speed (how quickly MEND can be unlocked) and security (slower hashing means an attacker can not brute force your password as quickly). If you want to tune the parameters to your specific machine you can run the [benchmark](https://github.com/P-H-C/phc-winner-argon2#benchmarks) script provided with the original source code. 
