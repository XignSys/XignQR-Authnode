var xignInScript = document.createElement("script");
document.body.querySelector("#content").appendChild(xignInScript);

xignInScript.setAttribute("referrerpolicy", "strict-origin-when-cross-origin");
xignInScript.setAttribute("type", "text/javascript");
xignInScript.setAttribute("src", "${managerUri}/js/xignqr-jslogin.umd.js");
xignInScript.onload = function () {
    const config = {
        managerUri: "${managerUri}",
        containerId: "login",
        clientId: "${clientId}",
        mode: XignQRLogin.XignLoginMode.PULL_TOKEN,
        useSSO: false,
        onAuthentication: function(authorizationCode){
            document.forms[0].elements['code'].value = authorizationCode;
            document.querySelector("input[type=submit]").click();
        },
        redirectUri: "${redirectUri}",
    };

    const login = new XignQRLogin.XignQRLogin(config);
    login.start();
    XignQRLogin.XignQRLogin.printVersion();
}

var loginDiv = document.createElement("div");
loginDiv.setAttribute("id", "login");
loginDiv.style.width="400px";
loginDiv.style.margin="auto";

document.body.querySelector("#content").insertBefore(loginDiv,document.body.querySelector("#content").querySelector(".container"));