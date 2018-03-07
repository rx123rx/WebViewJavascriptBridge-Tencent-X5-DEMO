1. jsBridge是所使用到的js bridge第三方库。这里将其与腾讯X5 webview结合一起使用，让低版本Android系统也有良好体验。

2. 请使用此库中的BridgeWebView代替原生的webview控件，还有一系列配置，请看demo。

3. jsBridge库中assets文件夹中的JsMessageExecutor.js文件是将Android和iOS jsBridge结合在一起的文件，里面会根据环境分别去使用Android或者iOS的bridge。

4. bridge使用方法可以查看下面的示例：

   ```
   window.lib.bridge.callNative('getKey', {}, function(result){
                   if (typeof result === "string") {
                       result = JSON.parse(result);
                   }
                   alert(result.packageName);
    });
   ```

   其中`getKey`为要调用的bridge功能的名称，类型为String类型, 对应的字段建议为**<u>functionName</u>**。`{}`为需要传递的参数，类型为json。

   `function(result)`为结果回调，APP原生会通过这个回调函数将期望的结果传递给H5，result类型为String。

