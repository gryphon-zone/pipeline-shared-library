# pipeline-shared-library

A collection of opinionated Jenkins "pipelines" for performing CI/CD on various project.

The goal is to reduce the `Jenkinsfile` for supported project types down to a handful of lines, while still providing
full functionality.

# Manual Jenkins Setup

In order for the library to fully function, some manual setup is required on the Jenkins master.

All of the below instructions use the placeholder `<jenkins>` to represent the host where Jenkins is installed,
e.g. `https://jenkins.example.com`, `https://example.com/jenkins`, or `https://jenkins.example.com:8443`.

## Library Installation

The first step is to install the library itself on the Jenkins master.
For ease of use, it's recommended to install the library at the global level.

To do this, first navigate to `<jenkins>/configure`, and scroll to the `Global Pipeline Libraries` section.
Then, add a new library named `gryphon-zone/pipeline-shared-library` with a default version of `master`
_(note: in the future there may be tagged releases instead of using the master branch)_

The following settings are recommended:

| Name | Value |
|------|-------|
| Load implicitly | `false` |
| Allow default version to be overridden | `true` |
| Include @Library changes in job recent changes | `true` |

Select `Modern SCM` as the retrieval method, and then configure either Git or Github, depending on your preferred setup.

## Credentials
The `pipeline-shared-library` assumes the presence of certain default credentials on the Jenkins instance in order to perform releases:

| Credential ID        | Type                | Description |
|----------------------|---------------------|-------------|
| `github-ssh`         | `sshUserPrivateKey` | SSH key with push access to [Github](https://github.com/) |
| `docker`             | `usernamePassword`  | Credentials for [Dockerhub](https://hub.docker.com)     |
| `ossrh`              | `usernamePassword`  | Credentials for [OSSRH Nexus](https://oss.sonatype.org) |
| `gpg-signing-key-id` | `string`            | ID of the GPG key |
| `gpg-signing-key`    | `file`              | File containing the GPG private key |

It is **strongly** recommended that the Jenkins instance have dedicated credentials
(e.g. don't re-use your personal credentials) to minimize the "blast radius" should the keys be compromised.

Instructions for configuring non-trivial credentials are below.

### GPG key

In order to release Maven artifacts, they must be signed via GPG.
Github has a [very helpful tutorial](https://help.github.com/en/articles/generating-a-new-gpg-key)
about setting up GPG keys, which covers the full process.

The short version is to perform the following steps:
1. Run `gpg --full-generate-key`
1. Select `RSA and RSA`
1. Select a key size of `4096`
1. Select key expiration date
    * A non-expiring key is not recommended 
    * Remember that keys will have to be rotated on the Jenkins master after expiration
1. Do not enter a passphrase
    * Note, not having a passphrase is insecure; future iterations of the PSL may support using one
1. Confirm information and save the key

At this point the key has been generated. To upload it into Jenkins, first run
```bash
gpg --list-secret-keys --keyid-format LONG
```
This should print something similar to the following:
```

------------------------------
sec   rsa4096/E7D24B5F40CF44D0 2019-06-30 [SC] [expires: 2024-06-28]
      FE7DAA796ADB4259B9F034729F13EF17022B5778
uid                 [ultimate] Jenkins <jenkins@gryphon.zone>
ssb   rsa4096/40F275B73CFA42C7 2019-06-30 [E] [expires: 2024-06-28]

```
In this case, `E7D24B5F40CF44D0` is the ID of the key we're after.

Next, to export the key secret, run:
```bash
gpg --export-secret-keys --output gpg-secret.key E7D24B5F40CF44D0
```
which will write the private key to the file `gpg-secret.key`.

Next, go to the Jenkins credential setup page (`<jenkins>/credentials/`), and create a new `string` secret named `gpg-signing-key-id`.
Set the value to your key's ID, in our example this would be `E7D24B5F40CF44D0`, and save the secret.

Next, add a new `file` secret named `gpg-signing-key`, and upload the `gpg-secret.key` we just created.

In order for the GPG key to work, you must send the public key to known keyservers so that external services
can verify it.

To do so, run the following commands:
```bash
for keyserver in 'http://keys.gnupg.net:11371' 'http://keyserver.ubuntu.com:11371' 'http://pool.sks-keyservers.net:11371'; do 
    gpg --send-keys --keyserver "${keyserver}" E7D24B5F40CF44D0
done
```
Replacing `E7D24B5F40CF44D0` with the ID of your key.

Finally, as an optional cleanup step, if you want to remove the generated key from your machine (since only Jenkins should be using it), you can run the following:
```bash
gpg --delete-secret-and-public-key E7D24B5F40CF44D0
```
Replacing `E7D24B5F40CF44D0` with the ID of your key



### SSH key
In order to push to Github, you must configure an SSH key with push access.

Github has another [very helpful tutorial](https://help.github.com/en/articles/generating-a-new-ssh-key-and-adding-it-to-the-ssh-agent)
about how to generate the key.

In short, the steps are as follows:
1. Run `ssh-keygen -t rsa -b 4096 -C "your_email@example.com"`
1. When prompted for where to save the key, change it to something like `/home/you/.ssh/jenkins_id_rsa`

After the key is generated, visit the Jenkins credentials page (`<jenkins>/credentials/`), and create a new
`SSH username with private key` named `github-ssh`.
Set the username to the value you use to log into Github with, and copy the contents of `/home/you/.ssh/jenkins_id_rsa`
into the private key field.

Finally, follow the steps on [this github article](https://help.github.com/en/articles/adding-a-new-ssh-key-to-your-github-account)
for instructions on adding the public key to your Github account so that Jenkins can perform pushes on your behalf.
