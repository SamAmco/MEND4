# MEND 
(My ENcrypted Data) provides a simple interface for encryption/decryption of sensitive data via a cross platform command line application and a compatible android app. 

## Motivation
I built MEND for password storage, journaling, and securing sensitive documents. It is pretty general purpose but the key idea is that encryption should be quick and easy from your device and only decryption should require authentication. You require only one password to "unlock" MEND at which point you can decrypt any/all of your encrypted data. MEND aims to provide confidentiality but does not make any particular guarantees with regard to authenticity or integrity. Typically I will point MEND at a cloud storage service (e.g. Dropbox or Google drive) and store all my passwords there encrypted. Decryption happens only on your local device and ideally you never even write the decrypted file to storage. This is I believe a superior password storage system than trusting a password management service like LastPass or OnePass as your cloud storage provider can not decrypt your data. The tradeoff is that there is no "forgot password" flow. If you forget your single password (or you lose your `config.xml`) your data is lost. For more strong opinions about passwords check out my [pass-words command line app](https://github.com/SamAmco/pass-words).

## Concepts
MEND uses hybrid encryption. A user has a randomly generated asymmetric key pair which is stored long term in a file called `config.xml`. To use MEND you must first set it up providing a password. This password is hashed using the Argon2 password hashing algorithm to generate an AES 256 bit key which is then used to encrypt your private key. Your public key and encrypted private key (as well as your password hashing parameters) are then stored in your `config.xml` file. This file does not contain confidential information but if you ever lose it you will not be able to decrypt your data any more as it contains not only your keys but also randomly generated salt and IV parameters used for encrypting your private key. 

Each encryption operation will first generate a new random symmetric key and IV (specifically AES 256), encrypt that with the public key of the asymmetric key pair, and then encrypt the data with the symmetric key. The encrypted symmetric key, IV, and encrypted data are then written or appended to a file.

In MEND there are two types of encrypted file. There are `.enc` files which are just files encrypted with the above process. Then there are `.mend` files (aka log files) which contain a log of entries with a date/time header and some encrypted text. You can append as many entries to a log file as you like, each entry will be encrypted with its own AES key and IV as per the above process.

In order to decrypt your data you must first `unlock` MEND. To do this you provide your password and MEND will decrypt and store your private key in plaintext. After unlocking MEND you can decrypt and view your data. When you are done you must remember to `lock` MEND again which securely deletes your decrypted private key file.

## Usage
To get started you will need to use the command line application to generate a `config.xml` (as the android app does not currently do this for you). Head over to [desktop-mend](desktop-mend/) for more details. 

After this if you want to use the android app check out the [mendroid](mendroid/) module.
