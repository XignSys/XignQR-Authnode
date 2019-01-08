/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


if (document.readyState !== 'loading') {
    callback();

} else {
    document.addEventListener("DOMContentLoaded", callback);
}
;


function callback() {
    let xignDivTag = document.createElement("div");
    xignDivTag.id = "xign_tag";
    xignDivTag.style.width = "200px";
    xignDivTag.style.marginRight = "auto";
    xignDivTag.style.marginLeft = "auto";
    xignDivTag.style.marginBottom = "40px";
    let loginContainer = document.getElementById("content");
    let loginButton = loginContainer.firstChild;
    loginContainer.insertBefore(xignDivTag, loginButton);

    let config = {
        redirect_uri: "http://10.0.32.50:90/test/red",
        client_id: "DJV0Vbf7N2yLVM9omPXXaUwp4",
        userinfo_selector: {
            nickname: 1,
            email: 1
        },
        mode: 11
    };
    var xignin = new XignINSSO(config);
    xignin.authenticate();
}



function XignINSSO(configuration) {
    const XMGRURL = "http://192.168.1.2:90/idp/XignMGR";
    const WSXMGRURL = XMGRURL.replace("http", "ws");
    const status_uri = XMGRURL + "/sso";
    const xtag = document.getElementById("xign_tag");
    var wSocket;
    var gui_channel;

    this.authenticate = function () {
        wSocket = new WebSocket(WSXMGRURL + "/login");
        wSocket.onopen = onopen;
        wSocket.onmessage = onmessage;
        wSocket.onclose = onclose;
    };

    var onopen = function () {

        if (!isMobileBrowser()) {
            requestAuthentication(configuration["userinfo_selector"],
                    configuration["client_id"],
                    configuration["redirect_uri"],
                    undefined,
                    configuration["mode"],
                    configuration["version"],
                    gui_channel);
        } else {
            requestAuthentication(configuration["userinfo_selector"],
                    configuration["client_id"],
                    configuration["redirect_uri"],
                    undefined,
                    8,
                    configuration["version"],
                    gui_channel);
        }
    };

    var onmessage = function (event) {
        var message = event.data;
        var response = JSON.parse(message);
        var eventType = response["eventType"];
        if (eventType === 7) {

        } else if (eventType === eventTypes.QR_CODE_RECEIVED) {
            handleQRCode(response["data"]);
        } else if (eventType === eventTypes.AUTH_RESULT) {
            handleAuthResult(response["data"]);
        } else if (eventType === eventTypes.AUTH_ABORTED) {
            handleAbort();
        } else if (eventType === eventTypes.GUI_STATUS) {
            handleGuiStatus();
        }
    };


    var onclose = function (event) {
        reset();
    };

    var handleAuthResult = function (data) {

        var status = data.success;
        if (status === true) {
            handleCode(data);
        } else if (status === false) {
            feedback("authentication failed due to reasons");
        }
    };


    var handleCode = function (data) {
        const refresh = data['redirectUri'];
        const code = data['code'];
        let ap;
        if (refresh.indexOf('?') > -1) {
            ap = "&code=";
        } else {
            ap = "?code=";
        }

        document.forms[0].elements['code'].value = refresh + ap + code;
        document.querySelector("input[type=submit]").click();
    };



    var handleQRCode = function (data) {
        if (!isMobileBrowser()) {
            if (configuration["mode"] !== 12) {
                if (configuration["mode"] !== 9) {
                    buildQRCode(xtag, data["qrCode"]);
                    function loaded() {
                        document.querySelector('#the_progress').style.backgroundColor = "red";
                        document.querySelector('#the_progress').style.width = "100%";
                    }
                    setTimeout(loaded, 100);
                } else {
                    sendToDesktop(data['token']);
                }
            } else {
                buildForm(xtag);
            }
        } else {
            let magicLink = document.createElement('a');
            if (isAndroid()) {
                magicLink.href = "intent://view?id=123#Intent;package=com.xignsys;S.qrcode=" + encodeURIComponent(data['token']) + ";scheme=XignQR;launchFlags=268435456;end;";
                document.querySelector('body').appendChild(magicLink);
                magicLink.click();
            } else if (isIOS()) {
                magicLink.href = "XignQR://?qrcode=" + encodeURIComponent(data['token']);
                document.querySelector('body').appendChild(magicLink);
                magicLink.click();
            } else {
                alert('plattform not supported');
            }
        }
    };

    var handleAbort = function () {
        feedback("Auth Aborted");
    };

    var handleGuiStatus = function (data) {

    };

    var sendMessage = function (obj) {
        wSocket.send(JSON.stringify(obj));
    };


    var requestAuthentication = function (ui_selector, client_id, refresh,
            transaction_data, mode, version) {
        const contextInformation = {};
        const request = {
            eventType: eventTypes.AUTH_REQUEST,
            redirect_uri: refresh,
            client_id: client_id,
            transaction: transaction_data,
            ui_selector: ui_selector,
            loginMode: mode,
            contextInformation: contextInformation,
            version: version,
            channel: random()
        };
        sendMessage(request);
    };

    var getContextInformation = function () {

        var client = new ClientJS();
        let os = client.getOS();
        let osVersion = client.getOSVersion();
        let browser = client.getBrowser();
        let browserVersion = client.getBrowserVersion();
        let engine = client.getEngine();
        let engineVersion = client.getEngineVersion();
        let isMobile = client.isMobile();

        let isIPad = false;
        let isIPhone = false;
        if (client.isMobileIOS()) {
            isIPhone = client.isIphone();
            isIPad = client.isIpad();
        }

        let vendor = client.getDeviceVendor();
        let fingerPrint = client.getFingerprint();
        let timezone = client.getTimeZone();
        let colorDepth = client.getColorDepth();
        let resolution = client.getCurrentResolution();

        let isJava = client.isJava();
        let javaVersion = 0;
        if (isJava) {
            javaVersion = client.getJavaVersion();
        }

        let isSilverlight = client.isSilverlight();
        let silverlightVersion = 0;
        if (isSilverlight) {
            silverlightVersion = client.getSilverlightVersion();
        }


        let isFlash = client.isFlash();
        let flashVersion = 0;
        if (isFlash) {
            flashVersion = client.getFlashVersion();
        }

        let language = client.getLanguage();

        let result = {
            os: os,
            osVersion: osVersion,
            browser: browser,
            browserVersion: browserVersion,
            engine: engine,
            engineVersion: engineVersion,
            isMobile: isMobile,
            isIPad: isIPad,
            isIPhone: isIPhone,
            vendor: vendor,
            fingerPrint: fingerPrint,
            timezone: timezone,
            colorDepth: colorDepth,
            resolution: resolution,
            isJava: isJava,
            javaVersion: javaVersion,
            isSilverlight: isSilverlight,
            silverlightVersion: silverlightVersion,
            isFlash: isFlash,
            flashVersion: flashVersion,
            language: language
        };

        return result;
    };

    var feedback = function (description) {

        var img = document.querySelector('#qrimage');
        if (img) {
            xtag.removeChild(img);
        }

        var progress = xtag.querySelector('#progress');
        progress.removeChild(progress.querySelector('#the_progress'));

        xtag.insertBefore(document.createTextNode(description), progress);
    };

    var STATE = STATE || {
        IGNORE: 0,
        REQUIRED: 1,
        OPTIONAL: 2
    };

    var eventTypes = eventTypes || {
        CONNECTION_OPENED: 0,
        AUTH_REQUEST: 1,
        QR_CODE_RECEIVED: 2,
        AUTH_RESULT: 3,
        AUTH_ABORTED: 4,
        GUI_STATUS: 6,
        CHECK_LOGIN: 9,
        NONCE_RECEIVED: 10,
        CHECK_LOGIN_SESSION: 11
    };

    var reset = function () {
        xtag.removeChild(document.getElementById("qrimage"));
        xtag.removeChild(document.getElementById("progress"));
        wSocket = new WebSocket(WSXMGRURL + "/login");
        wSocket.onopen = onopen;
        wSocket.onmessage = onmessage;
        wSocket.onclose = onclose;
    };

    var buildQRCode = function (xtag, img_url) {
        var resetButton = document.createElement("button");
        resetButton.id = "resetbutton";
        resetButton.addEventListener("click", reset, false);
        resetButton.appendChild(document.createTextNode("Refresh QRCode"));

        var progress_div = document.createElement("div");
        progress_div.id = "the_progress";
        progress_div.style.height = "100%";
        progress_div.style.width = "1%";
        progress_div.style.backgroundColor = "green";
        progress_div.style.transition = "background-color 60s linear , width 60s linear";

        var progress_container = document.createElement("div");
        progress_container.id = "progress";
        progress_container.style.width = "196px";
        progress_container.style.height = "10px";
        progress_container.margintop = "10px";
        progress_container.appendChild(progress_div);
        progress_container.appendChild(resetButton);

        var img = document.createElement("img");
        img.id = "qrimage";
        img.setAttribute("src", "data:image/png;base64," + img_url);
        img.style.width = "200px";
        xtag.appendChild(img);
        xtag.appendChild(progress_container);
    };

    var buildForm = function (xtag) {

        var userInput = document.createElement("input");
        userInput.type = "text";
        userInput.name = "username";
        userInput.id = "username";
        userInput.style.borderStyle = "none none solid none";
        userInput.style.borderWidth = "1px";
        userInput.style.width = "100%";

        var userLabel = document.createElement("label");
        userLabel.for = userInput.id;
        userLabel.innerHTML = "Username";

        var passwordInput = document.createElement("input");
        passwordInput.type = "password";
        passwordInput.name = "password";
        passwordInput.id = "password";
        passwordInput.style.borderStyle = "none none solid none";
        passwordInput.style.borderWidth = "1px";
        passwordInput.style.width = "100%";

        var passwordLabel = document.createElement("label");
        passwordLabel.for = passwordInput.id;
        passwordLabel.innerHTML = "Password";

        var submit = document.createElement("button");
        submit.type = "button";
        submit.value = "Login";
        submit.innerHTML = "Login";
        submit.onclick = function () {
            console.log(userInput.value + " " + passwordInput.value);
        };

        var form = document.createElement("form");
        form.action = "#";
        form.appendChild(userLabel);
        form.appendChild(userInput);
        form.appendChild(document.createElement("br"));
        form.appendChild(document.createElement("br"));
        form.appendChild(document.createElement("br"));
        form.appendChild(passwordLabel);
        form.appendChild(passwordInput);
        form.appendChild(document.createElement("br"));
        form.appendChild(document.createElement("br"));
        form.appendChild(document.createElement("br"));
        form.appendChild(submit);

        var div = document.createElement("div");
        div.style.width = "400px";
        div.style.height = "300px";
        div.appendChild(form);

        xtag.appendChild(div);
    };

    var sendToDesktop = function (qrContent) {
        var xhttp = new XMLHttpRequest();
        xhttp.open("POST", "http://127.0.0.1:20546/", true);
        xhttp.setRequestHeader("Content-type", "text/html");
        xhttp.send(qrContent);
    };

    var isMobileBrowser = function () {
        if (navigator.userAgent.match(/Android/i)
                || navigator.userAgent.match(/webOS/i)
                || navigator.userAgent.match(/iPhone/i)
                || navigator.userAgent.match(/iPad/i)
                || navigator.userAgent.match(/iPod/i)
                || navigator.userAgent.match(/BlackBerry/i)
                || navigator.userAgent.match(/Windows Phone/i)
                ) {
            return true;
        } else {
            return false;
        }
    };

    var isAndroid = function () {
        return navigator.userAgent.match(/Android/i);
    };

    var isIOS = function () {
        return navigator.userAgent.match(/iPhone/i)
                || navigator.userAgent.match(/iPad/i)
                || navigator.userAgent.match(/iPod/i);
    };
}

function random() {
    var text = "";
    var possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    for (var i = 0; i < 20; i++)
        text += possible.charAt(Math.floor(Math.random() * possible.length));
    return text;
}


