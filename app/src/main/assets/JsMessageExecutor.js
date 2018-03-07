// AMD and CommonJS-like environments, or CMD
(function (root, factory) {
    if (typeof define === 'function' && (define.amd || define.cmd)) {
        // AMD. Register as an anonymous module.
        // also support CMD pattern
        define(factory);
    } else if (typeof module === 'object' && module.exports) {
        // Node. Does not work with strict CommonJS, but
        // only CommonJS-like environments that support module.exports,
        // like Node.
        module.exports = factory();
    } else {
        // Browser globals (root is window)
        root.lib = root.lib || {};
        root.lib.bridge = factory();
    }
}(this, function () {
    var ua = navigator.userAgent;
    var appVersion = ua.match(/appVersion\(([^\(]+)\)/) && ua.match(/appVersion\(([^\(]+)\)/)[1];
    var bridgeVersion = ua.match(/bridgeLibVersion\(([^\(]+)\)/) && ua.match(/bridgeLibVersion\(([^\(]+)\)/)[1];
    var env = {
        app: {
            version: appVersion,
            is: (appVersion || bridgeVersion) ? true : false
        },
        compareVersion: function (v1, v2) {
            v1 = v1 ? v1.toString().split('.') : [];
            v2 = v2 ? v2.toString().split('.') : [];

            for (var i = 0; i < v1.length || i < v2.length; i++) {
                var n1 = parseInt(v1[i], 10), n2 = parseInt(v2[i], 10);

                if (window.isNaN(n1)) {
                    n1 = 0;
                }
                if (window.isNaN(n2)) {
                    n2 = 0;
                }
                if (n1 < n2) {
                    return -1;
                }
                else if (n1 > n2) {
                    return 1;
                }
            }
            return 0;
        }
    };
    var matched;

    if ((matched = ua.match(/Windows\sPhone\s(?:OS\s)?([\d\.]+)/))) {
        /**
         * @instance os
         * @member env
         * @property {String} name - 操作系统名称，比如Android/AndroidPad/iPhone/iPod/iPad/Windows Phone/unknown等
         * @property {Version} version - 操作系统版本号
         * @property {Boolean} isWindowsPhone - 是否是Windows Phone
         * @property {Boolean} isIPhone - 是否是iPhone/iTouch
         * @property {Boolean} isIPad - 是否是iPad
         * @property {Boolean} isIOS - 是否是iOS
         * @property {Boolean} isAndroid - 是否是Android手机
         * @property {Boolean} isAndroidPad - 是否是Android平板
         */
        env.os = {
            name: 'Windows Phone',
            isWindowsPhone: true,
            version: matched[1]
        }
    } else if (!!ua.match(/Safari/) && (matched = ua.match(/Android[\s\/]([\d\.]+)/))) {
        env.os = {
            name: (!!ua.match(/Mobile\s+Safari/)) ? 'Android' : 'AndroidPad',
            isAndroid: true,
            version: matched[1]
        };
    } else if ((matched = ua.match(/(iPhone|iPad|iPod)/))) {
        var name = matched[1];

        matched = ua.match(/OS ([\d_\.]+) like Mac OS X/);

        env.os = {
            name: name,
            isIPhone: (name === 'iPhone' || name === 'iPod'),
            isIPad: name === 'iPad',
            isIOS: true,
            version: matched[1].split('_').join('.')
        }
    } else {
        env.os = {
            name: 'unknown',
            version: '0.0.0'
        }
    }

    var incId = 1;
    var iframePool = [];
    var iframeLimit = 5;
    var IFRAME_PREFIX = 'iframe_';
    var JS_BRIDGE_CALLBACK_PREFIX = 'js_bridge_callback_';

    var webViewJavascriptBridgeCore = {
        callbackList: {},

        getSid: function () {
            return Math.floor(Math.random() * (1 << 50)) + '' + incId++;
        },

        registerCallback: function (sid, callback) {
            if (callback) {
                var callbackName = JS_BRIDGE_CALLBACK_PREFIX + sid;
                this.callbackList[callbackName] = callback;
                return callbackName;
            }
        },

        setupWebViewJavascriptBridge: function (callback) {
            if (window.WebViewJavascriptBridge) {
                return callback(window.WebViewJavascriptBridge)
              }

              if (window.WVJBCallbacks) {
                return window.WVJBCallbacks.push(callback)
              }

              window.WVJBCallbacks = [callback]

              var WVJBIframe = document.createElement('iframe')
              WVJBIframe.style.display = 'none'
              WVJBIframe.src = 'wvjbscheme://__BRIDGE_LOADED__'
              document.documentElement.appendChild(WVJBIframe)

              setTimeout(() => {
                document.documentElement.removeChild(WVJBIframe)
              }, 0)
        },

        callMethod: function (method, params, callback) {
            if (window.WebViewJavascriptBridge) {
                window.WebViewJavascriptBridge.send(method, params, callback);
            }
        }
    };

    if (env.os.isIOS) {
        webViewJavascriptBridgeCore.setupWebViewJavascriptBridge(function(_bridge) {
            /* Initialize your app here */
            _bridge.send = function (functionName, params, callback) {
                _bridge.callHandler('default', JSON.stringify({
                    msg: functionName,
                    params: params
                }), callback)
            }
            window.WebViewJavascriptBridge = bridge
        });
    } else if (env.os.isAndroid) {
        console.log('android bridge init')
        webViewJavascriptBridgeCore.setupWebViewJavascriptBridge(function(_bridge) {
            /* Initialize your app here */
            _bridge.send = function (functionName, params, callback) {
                if (typeof params === 'string') {
                    params = JSON.parse(params);
                }
                params['functionName'] = functionName;
                _bridge.callHandler('default', JSON.stringify(params), callback)
            }
            window.WebViewJavascriptBridge = bridge
        });
    }

    var bridge = {
        // bridge版本号
        version: navigator.userAgent.match(/bridgeLibVersion\(([^\(]+)\)/) && navigator.userAgent.match(/bridgeLibVersion\(([^\(]+)\)/)[1],
        /**
         * 桥接调用方式
         * @param method {String} 桥接方法
         * @param async {Boolean} 桥接调用方式, 可选,默认为false
         * @param params {Object} 桥接参数
         * @param callback {Function} 回调函数
         */
        callNative: function (method, params, callback) {
            // iOS系统且bridge在10.0及以上的版本,使用新的调用方式
            console.log(params)
            webViewJavascriptBridgeCore.callMethod(method, JSON.stringify(params), callback);
        }
    };

    return bridge;
}));

