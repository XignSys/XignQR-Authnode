<!--
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright ${data.get('yyyy')} ForgeRock AS.
-->
# XignQR-Authnode

An authentication node for integration with the [XignQR Authentication System](https://xignsys.com). Using XignQR you will be able to authenticate against OpenAM > v6.5 using your smartphone.

# XignQR Authentication

XignQR offers you the ability to login and get access to ICT Systems, e. g. ForgeRock, password-less via Smartphone, backed by a high security SaaS platform. Use XignQR as 1-, 2- and M-Factor Authentication tool. Easily integrated via QR Code or Push-Authentication to mitigate cyberattacks like Phishing.


# Installation
Copy the .jar file from the ../target directory into the ../web-container/webapps/openam/WEB-INF/lib directory where AM is deployed.  Restart the web container to pick up the new node.  The node will then appear in the authentication trees components palette.

**USAGE HERE**

To use authentication via smartphone you have to download the XignQR App and register yourself at [XignQR Public](https://public.xignsys.com) to be able to configure your client (aka ForgeRock OpenAM).

Fill in the provided form

![ScreenShot](./images/form_register.png)

A QR Code is sent to you via email. The qr code is used to enroll your smartphone in the XignQR System. As soon as you have received the qr code follow these instructions:

1. Open up the app, type in the transport pin you have provided when registering yourself. 

2. The App then prompts for some authentication factors

3. Press personalize to enroll your device

4. The personalization process takes about 30 seconds until you are enrolled

**Configuration**

Log in to  [XignQR Public](https://prod.v22017042416647763.bestsrv.de/m/start) and register your client.

![ScreenShot](./images/register_client.png)

After Registration, select your newly created client and use the controls to download the properties file

![ScreenShot](./images/download_button.png)

Place the downloaded Properties on the filesystem of your OpenAM Installtion and provide the path in the configuration of the auth node. And map the attributes that should be matched.

**Example XignQR**

This is the straight forward configuration for the use of _XignQR for MFA_

![ScreenShot](./images/forgrerock_qr_tree_nodes.png)

Via the drop-down mapping menu, you will be able to configure, which data, which is delievered from the XignQR-System, should match the identity attributes in your identity repository.

**Example XignPush**

This is the __recommended__ configuration for _XignQR as a Second Factor_. This type  of configuration prevents spamming arbitrary users with Push-Authentication requests, since the password has to be correct to trigger a push notification.

![ScreenShot](./images/forgerock_push_tree_nodes.png)

Via the drop-down mapping menu, you will be able to configure, which data, which is delievered from the XignQR-System, should match the identity attributes in your identity repository.

# Authentication
**XignQR**

Open up your personalized XignQR App and scan the displayed qr code with the integrated qr code scanner.

![ScreenShot](./images/login_xignqr.png)

After scanning the qr code, the app gives a haptic feedback, and you'll see that the app communicates with the XignQR backend system.
You'll be prompted to accept or decline the delivery of the displayed attributes to openam.

<img src="./images/prompt_attributes.jpg" width="300px"/>

After you have accepted the delivery of the attributes, you'll be prompted to authenticate yourself against the XignQR App. 
If you have configured a fingerprint when you personalized your device, you'll be prompted for fingerprint authentication,
if not you'll be prompted to enter your personal PIN.

<img src="./images/prompt_authfactor.jpg" width="300px"/>


**XignPush**

When using XignPush you'll have to enter your XignQR username or email (depends on mapping) to be able to log in. 

<img src="./images/forgerock_user_name.png"/>

After that enter your password for your identity.

<img src="./images/forgerock_password.png"/>

You will see, that openam is waiting for the authentication response from xignqr.

<img src="./images/forgerock_push_waiting.png"/>

The XignQR system will deliver a push notification to your device. When you press on the notification, the XignQR app will open up.
And the flow will be similar to that of using XignQR.

<img src="./images/push_notification.jpg" width="300px"/>

If you open up the notification, the authentication procedure will start, afterwards you'll be logged into your mapped account.

<img src="./images/forgerock_profile.jpg" width="300px"/>

[forgerock_platform]: https://www.forgerock.com/platform/  


