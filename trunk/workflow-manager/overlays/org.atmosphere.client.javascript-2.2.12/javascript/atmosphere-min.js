(function(a,b){if(typeof define==="function"&&define.amd){define(b)
}else{if(typeof exports!=="undefined"){module.exports=b()
}else{a.atmosphere=b()
}}}(this,function(){var c="2.2.12-javascript",a={},e,d=false,h=[],g=[],f=0,b=Object.prototype.hasOwnProperty;
a={onError:function(i){},onClose:function(i){},onOpen:function(i){},onReopen:function(i){},onMessage:function(i){},onReconnect:function(j,i){},onMessagePublished:function(i){},onTransportFailure:function(j,i){},onLocalMessage:function(i){},onFailureToReconnect:function(j,i){},onClientTimeout:function(i){},onOpenAfterResume:function(i){},WebsocketApiAdapter:function(j){var i,k;
j.onMessage=function(l){k.onmessage({data:l.responseBody})
};
j.onMessagePublished=function(l){k.onmessage({data:l.responseBody})
};
j.onOpen=function(l){k.onopen(l)
};
k={close:function(){i.close()
},send:function(l){i.push(l)
},onmessage:function(l){},onopen:function(l){},onclose:function(l){},onerror:function(l){}};
i=new a.subscribe(j);
return k
},AtmosphereRequest:function(ae){var q={timeout:300000,method:"GET",headers:{},contentType:"",callback:null,url:"",data:"",suspend:true,maxRequest:-1,reconnect:true,maxStreamingLength:10000000,lastIndex:0,logLevel:"info",requestCount:0,fallbackMethod:"GET",fallbackTransport:"streaming",transport:"long-polling",webSocketImpl:null,webSocketBinaryType:null,dispatchUrl:null,webSocketPathDelimiter:"@@",enableXDR:false,rewriteURL:false,attachHeadersAsQueryString:true,executeCallbackBeforeReconnect:false,readyState:0,withCredentials:false,trackMessageLength:false,messageDelimiter:"|",connectTimeout:-1,reconnectInterval:0,dropHeaders:true,uuid:0,async:true,shared:false,readResponsesHeaders:false,maxReconnectOnClose:5,enableProtocol:true,pollingInterval:0,heartbeat:{client:null,server:null},ackInterval:0,closeAsync:false,reconnectOnServerError:true,onError:function(aJ){},onClose:function(aJ){},onOpen:function(aJ){},onMessage:function(aJ){},onReopen:function(aK,aJ){},onReconnect:function(aK,aJ){},onMessagePublished:function(aJ){},onTransportFailure:function(aK,aJ){},onLocalMessage:function(aJ){},onFailureToReconnect:function(aK,aJ){},onClientTimeout:function(aJ){},onOpenAfterResume:function(aJ){}};
var at={status:200,reasonPhrase:"OK",responseBody:"",messages:[],headers:[],state:"messageReceived",transport:"polling",error:null,request:null,partialMessage:"",errorHandled:false,closedByClientTimeout:false,ffTryingReconnect:false};
var ax=null;
var ah=null;
var A=null;
var o=null;
var Y=null;
var v=true;
var az=0;
var K=0;
var al="X";
var aH=false;
var R=null;
var i;
var ay=null;
var S=a.util.now();
var z;
var aG;
var Z=false;
ap(ae);
function ak(){v=true;
aH=false;
az=0;
ax=null;
ah=null;
A=null;
o=null
}function V(){m();
ak()
}function x(aJ){if(aJ=="debug"){return q.logLevel==="debug"
}else{if(aJ=="info"){return q.logLevel==="info"||q.logLevel==="debug"
}else{if(aJ=="warn"){return q.logLevel==="warn"||q.logLevel==="info"||q.logLevel==="debug"
}else{if(aJ=="error"){return q.logLevel==="error"||q.logLevel==="warn"||q.logLevel==="info"||q.logLevel==="debug"
}else{return false
}}}}}function aI(aJ){if(x("debug")){a.util.debug(new Date()+" Atmosphere: "+aJ)
}}function J(aK,aJ){if(at.partialMessage===""&&(aJ.transport==="streaming")&&(aK.responseText.length>aJ.maxStreamingLength)){return true
}return false
}function E(){if(q.enableProtocol&&!q.firstMessage){var aL="X-Atmosphere-Transport=close&X-Atmosphere-tracking-id="+q.uuid;
a.util.each(q.headers,function(aN,aP){var aO=a.util.isFunction(aP)?aP.call(this,q,q,at):aP;
if(aO!=null){aL+="&"+encodeURIComponent(aN)+"="+encodeURIComponent(aO)
}});
var aJ=q.url.replace(/([?&])_=[^&]*/,aL);
aJ=aJ+(aJ===q.url?(/\?/.test(q.url)?"&":"?")+aL:"");
var aK={connected:false};
var aM=new a.AtmosphereRequest(aK);
aM.connectTimeout=q.connectTimeout;
aM.attachHeadersAsQueryString=false;
aM.dropHeaders=true;
aM.url=aJ;
aM.contentType="text/plain";
aM.transport="polling";
aM.method="GET";
aM.data="";
aM.heartbeat=null;
if(q.enableXDR){aM.enableXDR=q.enableXDR
}aM.async=q.closeAsync;
an("",aM)
}}function I(){aI("Closing (AtmosphereRequest._close() called)");
aH=true;
if(q.reconnectId){clearTimeout(q.reconnectId);
delete q.reconnectId
}if(q.heartbeatTimer){clearTimeout(q.heartbeatTimer)
}q.reconnect=false;
at.request=q;
at.state="unsubscribe";
at.responseBody="";
at.status=408;
at.partialMessage="";
aj();
E();
m()
}function m(){at.partialMessage="";
if(q.id){clearTimeout(q.id)
}if(q.heartbeatTimer){clearTimeout(q.heartbeatTimer)
}if(q.reconnectId){clearTimeout(q.reconnectId);
delete q.reconnectId
}if(o!=null){o.close();
o=null
}if(Y!=null){Y.abort();
Y=null
}if(A!=null){A.abort();
A=null
}if(ax!=null){if(ax.canSendMessage){aI("invoking .close() on WebSocket object");
ax.close()
}ax=null
}if(ah!=null){ah.close();
ah=null
}ai()
}function ai(){if(i!=null){clearInterval(z);
document.cookie=aG+"=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
i.signal("close",{reason:"",heir:!aH?S:(i.get("children")||[])[0]});
i.close()
}if(ay!=null){ay.close()
}}function ap(aJ){V();
q=a.util.extend(q,aJ);
q.mrequest=q.reconnect;
if(!q.reconnect){q.reconnect=true
}}function av(){return q.webSocketImpl!=null||window.WebSocket||window.MozWebSocket
}function au(){var aK=a.util.getAbsoluteURL(q.url.toLowerCase());
var aL=/^([\w\+\.\-]+:)(?:\/\/([^\/?#:]*)(?::(\d+))?)?/.exec(aK);
var aJ=!!(aL&&(aL[1]!=window.location.protocol||aL[2]!=window.location.hostname||(aL[3]||(aL[1]==="http:"?80:443))!=(window.location.port||(window.location.protocol==="http:"?80:443))));
return window.EventSource&&(!aJ||!a.util.browser.safari||a.util.browser.vmajor>=7)
}function ab(){if(q.shared){ay=aE(q);
if(ay!=null){if(x("debug")){a.util.debug("Storage service available. All communication will be local")
}if(ay.open(q)){return
}}if(x("debug")){a.util.debug("No Storage service available.")
}ay=null
}q.firstMessage=f==0?true:false;
q.isOpen=false;
q.ctime=a.util.now();
if(q.uuid===0){q.uuid=f
}at.closedByClientTimeout=false;
if(q.transport!=="websocket"&&q.transport!=="sse"){M(q)
}else{if(q.transport==="websocket"){if(!av()){aA("Websocket is not supported, using request.fallbackTransport ("+q.fallbackTransport+")")
}else{ag(false)
}}else{if(q.transport==="sse"){if(!au()){aA("Server Side Events(SSE) is not supported, using request.fallbackTransport ("+q.fallbackTransport+")")
}else{D(false)
}}}}}function aE(aN){var aO,aM,aR,aJ="atmosphere-"+aN.url,aK={storage:function(){function aS(aW){if(aW.key===aJ&&aW.newValue){aL(aW.newValue)
}}if(!a.util.storage){return
}var aV=window.localStorage,aT=function(aW){return a.util.parseJSON(aV.getItem(aJ+"-"+aW))
},aU=function(aW,aX){aV.setItem(aJ+"-"+aW,a.util.stringifyJSON(aX))
};
return{init:function(){aU("children",aT("children").concat([S]));
a.util.on(window,"storage",aS);
return aT("opened")
},signal:function(aW,aX){aV.setItem(aJ,a.util.stringifyJSON({target:"p",type:aW,data:aX}))
},close:function(){var aW=aT("children");
a.util.off(window,"storage",aS);
if(aW){if(aP(aW,aN.id)){aU("children",aW)
}}}}
},windowref:function(){var aS=window.open("",aJ.replace(/\W/g,""));
if(!aS||aS.closed||!aS.callbacks){return
}return{init:function(){aS.callbacks.push(aL);
aS.children.push(S);
return aS.opened
},signal:function(aT,aU){if(!aS.closed&&aS.fire){aS.fire(a.util.stringifyJSON({target:"p",type:aT,data:aU}))
}},close:function(){if(!aR){aP(aS.callbacks,aL);
aP(aS.children,S)
}}}
}};
function aP(aV,aU){var aS,aT=aV.length;
for(aS=0;
aS<aT;
aS++){if(aV[aS]===aU){aV.splice(aS,1)
}}return aT!==aV.length
}function aL(aS){var aU=a.util.parseJSON(aS),aT=aU.data;
if(aU.target==="c"){switch(aU.type){case"open":W("opening","local",q);
break;
case"close":if(!aR){aR=true;
if(aT.reason==="aborted"){I()
}else{if(aT.heir===S){ab()
}else{setTimeout(function(){ab()
},100)
}}}break;
case"message":l(aT,"messageReceived",200,aN.transport);
break;
case"localMessage":G(aT);
break
}}}function aQ(){var aS=new RegExp("(?:^|; )("+encodeURIComponent(aJ)+")=([^;]*)").exec(document.cookie);
if(aS){return a.util.parseJSON(decodeURIComponent(aS[2]))
}}aO=aQ();
if(!aO||a.util.now()-aO.ts>1000){return
}aM=aK.storage()||aK.windowref();
if(!aM){return
}return{open:function(){var aS;
z=setInterval(function(){var aT=aO;
aO=aQ();
if(!aO||aT.ts===aO.ts){aL(a.util.stringifyJSON({target:"c",type:"close",data:{reason:"error",heir:aT.heir}}))
}},1000);
aS=aM.init();
if(aS){setTimeout(function(){W("opening","local",aN)
},50)
}return aS
},send:function(aS){aM.signal("send",aS)
},localSend:function(aS){aM.signal("localSend",a.util.stringifyJSON({id:S,event:aS}))
},close:function(){if(!aH){clearInterval(z);
aM.signal("close");
aM.close()
}}}
}function aF(){var aK,aJ="atmosphere-"+q.url,aO={storage:function(){function aP(aR){if(aR.key===aJ&&aR.newValue){aL(aR.newValue)
}}if(!a.util.storage){return
}var aQ=window.localStorage;
return{init:function(){a.util.on(window,"storage",aP)
},signal:function(aR,aS){aQ.setItem(aJ,a.util.stringifyJSON({target:"c",type:aR,data:aS}))
},get:function(aR){return a.util.parseJSON(aQ.getItem(aJ+"-"+aR))
},set:function(aR,aS){aQ.setItem(aJ+"-"+aR,a.util.stringifyJSON(aS))
},close:function(){a.util.off(window,"storage",aP);
aQ.removeItem(aJ);
aQ.removeItem(aJ+"-opened");
aQ.removeItem(aJ+"-children")
}}
},windowref:function(){var aQ=aJ.replace(/\W/g,""),aP=document.getElementById(aQ),aR;
if(!aP){aP=document.createElement("div");
aP.id=aQ;
aP.style.display="none";
aP.innerHTML='<iframe name="'+aQ+'" />';
document.body.appendChild(aP)
}aR=aP.firstChild.contentWindow;
return{init:function(){aR.callbacks=[aL];
aR.fire=function(aS){var aT;
for(aT=0;
aT<aR.callbacks.length;
aT++){aR.callbacks[aT](aS)
}}
},signal:function(aS,aT){if(!aR.closed&&aR.fire){aR.fire(a.util.stringifyJSON({target:"c",type:aS,data:aT}))
}},get:function(aS){return !aR.closed?aR[aS]:null
},set:function(aS,aT){if(!aR.closed){aR[aS]=aT
}},close:function(){}}
}};
function aL(aP){var aR=a.util.parseJSON(aP),aQ=aR.data;
if(aR.target==="p"){switch(aR.type){case"send":u(aQ);
break;
case"localSend":G(aQ);
break;
case"close":I();
break
}}}R=function aN(aP){aK.signal("message",aP)
};
function aM(){document.cookie=aG+"="+encodeURIComponent(a.util.stringifyJSON({ts:a.util.now()+1,heir:(aK.get("children")||[])[0]}))+"; path=/"
}aK=aO.storage()||aO.windowref();
aK.init();
if(x("debug")){a.util.debug("Installed StorageService "+aK)
}aK.set("children",[]);
if(aK.get("opened")!=null&&!aK.get("opened")){aK.set("opened",false)
}aG=encodeURIComponent(aJ);
aM();
z=setInterval(aM,1000);
i=aK
}function W(aL,aO,aK){if(q.shared&&aO!=="local"){aF()
}if(i!=null){i.set("opened",true)
}aK.close=function(){I()
};
if(az>0&&aL==="re-connecting"){aK.isReopen=true;
r(at)
}else{if(at.error==null){at.request=aK;
var aM=at.state;
at.state=aL;
var aJ=at.transport;
at.transport=aO;
var aN=at.responseBody;
aj();
at.responseBody=aN;
at.state=aM;
at.transport=aJ
}}}function aC(aL){aL.transport="jsonp";
var aK=q,aJ;
if((aL!=null)&&(typeof(aL)!=="undefined")){aK=aL
}Y={open:function(){var aO="atmosphere"+(++S);
function aM(){aK.lastIndex=0;
if(aK.openId){clearTimeout(aK.openId)
}if(aK.heartbeatTimer){clearTimeout(aK.heartbeatTimer)
}if(aK.reconnect&&az++<aK.maxReconnectOnClose){W("re-connecting",aK.transport,aK);
ao(Y,aK,aL.reconnectInterval);
aK.openId=setTimeout(function(){aa(aK)
},aK.reconnectInterval+1000)
}else{U(0,"maxReconnectOnClose reached")
}}function aN(){var aP=aK.url;
if(aK.dispatchUrl!=null){aP+=aK.dispatchUrl
}var aR=aK.data;
if(aK.attachHeadersAsQueryString){aP=p(aK);
if(aR!==""){aP+="&X-Atmosphere-Post-Body="+encodeURIComponent(aR)
}aR=""
}var aQ=document.head||document.getElementsByTagName("head")[0]||document.documentElement;
aJ=document.createElement("script");
aJ.src=aP+"&jsonpTransport="+aO;
aJ.clean=function(){aJ.clean=aJ.onerror=aJ.onload=aJ.onreadystatechange=null;
if(aJ.parentNode){aJ.parentNode.removeChild(aJ)
}if(++aL.scriptCount===2){aL.scriptCount=1;
aM()
}};
aJ.onload=aJ.onreadystatechange=function(){aI("jsonp.onload");
if(!aJ.readyState||/loaded|complete/.test(aJ.readyState)){aJ.clean()
}};
aJ.onerror=function(){aI("jsonp.onerror");
aL.scriptCount=1;
aJ.clean()
};
aQ.insertBefore(aJ,aQ.firstChild)
}window[aO]=function(aR){aI("jsonp.window");
aL.scriptCount=0;
if(aK.reconnect&&aK.maxRequest===-1||aK.requestCount++<aK.maxRequest){if(!aK.executeCallbackBeforeReconnect){ao(Y,aK,aK.pollingInterval)
}if(aR!=null&&typeof aR!=="string"){try{aR=aR.message
}catch(aQ){}}var aP=s(aR,aK,at);
if(!aP){l(at.responseBody,"messageReceived",200,aK.transport)
}if(aK.executeCallbackBeforeReconnect){ao(Y,aK,aK.pollingInterval)
}k(aK)
}else{a.util.log(q.logLevel,["JSONP reconnect maximum try reached "+q.requestCount]);
U(0,"maxRequest reached")
}};
setTimeout(function(){aN()
},50)
},abort:function(){if(aJ&&aJ.clean){aJ.clean()
}}};
Y.open()
}function aw(aJ){if(q.webSocketImpl!=null){return q.webSocketImpl
}else{if(window.WebSocket){return new WebSocket(aJ)
}else{return new MozWebSocket(aJ)
}}}function w(){return p(q,a.util.getAbsoluteURL(q.webSocketUrl||q.url)).replace(/^http/,"ws")
}function T(){var aJ=p(q);
return aJ
}function D(aK){at.transport="sse";
var aJ=T();
if(x("debug")){a.util.debug("Invoking executeSSE");
a.util.debug("Using URL: "+aJ)
}if(aK&&!q.reconnect){if(ah!=null){m()
}return
}try{ah=new EventSource(aJ,{withCredentials:q.withCredentials})
}catch(aL){U(0,aL);
aA("SSE failed. Downgrading to fallback transport and resending");
return
}if(q.connectTimeout>0){q.id=setTimeout(function(){if(!aK){m()
}},q.connectTimeout)
}ah.onopen=function(aM){aI("sse.onopen");
k(q);
if(x("debug")){a.util.debug("SSE successfully opened")
}if(!q.enableProtocol){if(!aK){W("opening","sse",q)
}else{W("re-opening","sse",q)
}}else{if(q.isReopen){q.isReopen=false;
W("re-opening",q.transport,q)
}}aK=true;
if(q.method==="POST"){at.state="messageReceived";
ah.send(q.data)
}};
ah.onmessage=function(aN){aI("sse.onmessage");
k(q);
if(!q.enableXDR&&aN.origin&&aN.origin!==window.location.protocol+"//"+window.location.host){a.util.log(q.logLevel,["Origin was not "+window.location.protocol+"//"+window.location.host]);
return
}at.state="messageReceived";
at.status=200;
aN=aN.data;
var aM=s(aN,q,at);
if(!aM){aj();
at.responseBody="";
at.messages=[]
}};
ah.onerror=function(aM){aI("sse.onerror");
clearTimeout(q.id);
if(q.heartbeatTimer){clearTimeout(q.heartbeatTimer)
}if(at.closedByClientTimeout){return
}af(aK);
m();
if(aH){a.util.log(q.logLevel,["SSE closed normally"])
}else{if(!aK){aA("SSE failed. Downgrading to fallback transport and resending")
}else{if(q.reconnect&&(at.transport==="sse")){if(az++<q.maxReconnectOnClose){W("re-connecting",q.transport,q);
if(q.reconnectInterval>0){q.reconnectId=setTimeout(function(){D(true)
},q.reconnectInterval)
}else{D(true)
}at.responseBody="";
at.messages=[]
}else{a.util.log(q.logLevel,["SSE reconnect maximum try reached "+az]);
U(0,"maxReconnectOnClose reached")
}}}}}
}function ag(aK){at.transport="websocket";
var aJ=w(q.url);
if(x("debug")){a.util.debug("Invoking executeWebSocket, using URL: "+aJ)
}if(aK&&!q.reconnect){if(ax!=null){m()
}return
}ax=aw(aJ);
if(q.webSocketBinaryType!=null){ax.binaryType=q.webSocketBinaryType
}if(q.connectTimeout>0){q.id=setTimeout(function(){if(!aK){var aN={code:1002,reason:"",wasClean:false};
ax.onclose(aN);
try{m()
}catch(aO){}return
}},q.connectTimeout)
}ax.onopen=function(aO){aI("websocket.onopen");
k(q);
d=false;
if(x("debug")){a.util.debug("Websocket successfully opened")
}var aN=aK;
if(ax!=null){ax.canSendMessage=true
}if(!q.enableProtocol){aK=true;
if(aN){W("re-opening","websocket",q)
}else{W("opening","websocket",q)
}}if(ax!=null){if(q.method==="POST"){at.state="messageReceived";
ax.send(q.data)
}}};
ax.onmessage=function(aP){aI("websocket.onmessage");
k(q);
if(q.enableProtocol){aK=true
}at.state="messageReceived";
at.status=200;
aP=aP.data;
var aN=typeof(aP)==="string";
if(aN){var aO=s(aP,q,at);
if(!aO){aj();
at.responseBody="";
at.messages=[]
}}else{aP=t(q,aP);
if(aP===""){return
}at.responseBody=aP;
aj();
at.responseBody=null
}};
ax.onerror=function(aN){aI("websocket.onerror");
clearTimeout(q.id);
if(q.heartbeatTimer){clearTimeout(q.heartbeatTimer)
}};
ax.onclose=function(aN){aI("websocket.onclose");
clearTimeout(q.id);
if(at.state==="closed"){return
}var aO=aN.reason;
if(aO===""){switch(aN.code){case 1000:aO="Normal closure; the connection successfully completed whatever purpose for which it was created.";
break;
case 1001:aO="The endpoint is going away, either because of a server failure or because the browser is navigating away from the page that opened the connection.";
break;
case 1002:aO="The endpoint is terminating the connection due to a protocol error.";
break;
case 1003:aO="The connection is being terminated because the endpoint received data of a type it cannot accept (for example, a text-only endpoint received binary data).";
break;
case 1004:aO="The endpoint is terminating the connection because a data frame was received that is too large.";
break;
case 1005:aO="Unknown: no status code was provided even though one was expected.";
break;
case 1006:aO="Connection was closed abnormally (that is, with no close frame being sent).";
break
}}if(x("warn")){a.util.warn("Websocket closed, reason: "+aO+" - wasClean: "+aN.wasClean)
}if(at.closedByClientTimeout||d){if(q.reconnectId){clearTimeout(q.reconnectId);
delete q.reconnectId
}return
}af(aK);
at.state="closed";
if(aH){a.util.log(q.logLevel,["Websocket closed normally"])
}else{if(!aK){aA("Websocket failed on first connection attempt. Downgrading to "+q.fallbackTransport+" and resending")
}else{if(q.reconnect&&at.transport==="websocket"){m();
if(az++<q.maxReconnectOnClose){W("re-connecting",q.transport,q);
if(q.reconnectInterval>0){q.reconnectId=setTimeout(function(){at.responseBody="";
at.messages=[];
ag(true)
},q.reconnectInterval)
}else{at.responseBody="";
at.messages=[];
ag(true)
}}else{a.util.log(q.logLevel,["Websocket reconnect maximum try reached "+q.requestCount]);
if(x("warn")){a.util.warn("Websocket error, reason: "+aN.reason)
}U(0,"maxReconnectOnClose reached")
}}}}};
var aL=navigator.userAgent.toLowerCase();
var aM=aL.indexOf("android")>-1;
if(aM&&ax.url===undefined){ax.onclose({reason:"Android 4.1 does not support websockets.",wasClean:false})
}}function t(aN,aM){var aL=aM;
if(aN.transport==="polling"){return aL
}if(aN.enableProtocol&&aN.firstMessage&&a.util.trim(aM).length!==0){var aO=aN.trackMessageLength?1:0;
var aK=aM.split(aN.messageDelimiter);
if(aK.length<=aO+1){return aL
}aN.firstMessage=false;
aN.uuid=a.util.trim(aK[aO]);
if(aK.length<=aO+2){a.util.log("error",["Protocol data not sent by the server. If you enable protocol on client side, be sure to install JavascriptProtocol interceptor on server side.Also note that atmosphere-runtime 2.2+ should be used."])
}K=parseInt(a.util.trim(aK[aO+1]),10);
al=aK[aO+2];
if(aN.transport!=="long-polling"){aa(aN)
}f=aN.uuid;
aL="";
aO=aN.trackMessageLength?4:3;
if(aK.length>aO+1){for(var aJ=aO;
aJ<aK.length;
aJ++){aL+=aK[aJ];
if(aJ+1!==aK.length){aL+=aN.messageDelimiter
}}}if(aN.ackInterval!==0){setTimeout(function(){u("...ACK...")
},aN.ackInterval)
}}else{if(aN.enableProtocol&&aN.firstMessage&&a.util.browser.msie&&+a.util.browser.version.split(".")[0]<10){a.util.log(q.logLevel,["Receiving unexpected data from IE"])
}else{aa(aN)
}}return aL
}function k(aJ){clearTimeout(aJ.id);
if(aJ.timeout>0&&aJ.transport!=="polling"){aJ.id=setTimeout(function(){aD(aJ);
E();
m()
},aJ.timeout)
}}function aD(aJ){at.closedByClientTimeout=true;
at.state="closedByClient";
at.responseBody="";
at.status=408;
at.messages=[];
aj()
}function U(aJ,aK){m();
clearTimeout(q.id);
at.state="error";
at.reasonPhrase=aK;
at.responseBody="";
at.status=aJ;
at.messages=[];
aj()
}function s(aN,aM,aJ){aN=t(aM,aN);
if(aN.length===0){return true
}aJ.responseBody=aN;
if(aM.trackMessageLength){aN=aJ.partialMessage+aN;
var aL=[];
var aK=aN.indexOf(aM.messageDelimiter);
if(aK!=-1){while(aK!==-1){var aP=aN.substring(0,aK);
var aO=+aP;
if(isNaN(aO)){throw new Error('message length "'+aP+'" is not a number')
}aK+=aM.messageDelimiter.length;
if(aK+aO>aN.length){aK=-1
}else{aL.push(aN.substring(aK,aK+aO));
aN=aN.substring(aK+aO,aN.length);
aK=aN.indexOf(aM.messageDelimiter)
}}aJ.partialMessage=aN;
if(aL.length!==0){aJ.responseBody=aL.join(aM.messageDelimiter);
aJ.messages=aL;
return false
}else{aJ.responseBody="";
aJ.messages=[];
return true
}}}aJ.responseBody=aN;
aJ.messages=[aN];
return false
}function aA(aJ){a.util.log(q.logLevel,[aJ]);
if(typeof(q.onTransportFailure)!=="undefined"){q.onTransportFailure(aJ,q)
}else{if(typeof(a.util.onTransportFailure)!=="undefined"){a.util.onTransportFailure(aJ,q)
}}q.transport=q.fallbackTransport;
var aK=q.connectTimeout===-1?0:q.connectTimeout;
if(q.reconnect&&q.transport!=="none"||q.transport==null){q.method=q.fallbackMethod;
at.transport=q.fallbackTransport;
q.fallbackTransport="none";
if(aK>0){q.reconnectId=setTimeout(function(){ab()
},aK)
}else{ab()
}}else{U(500,"Unable to reconnect with fallback transport")
}}function p(aL,aJ){var aK=q;
if((aL!=null)&&(typeof(aL)!=="undefined")){aK=aL
}if(aJ==null){aJ=aK.url
}if(!aK.attachHeadersAsQueryString){return aJ
}if(aJ.indexOf("X-Atmosphere-Framework")!==-1){return aJ
}aJ+=(aJ.indexOf("?")!==-1)?"&":"?";
aJ+="X-Atmosphere-tracking-id="+aK.uuid;
aJ+="&X-Atmosphere-Framework="+c;
aJ+="&X-Atmosphere-Transport="+aK.transport;
if(aK.trackMessageLength){aJ+="&X-Atmosphere-TrackMessageSize=true"
}if(aK.heartbeat!==null&&aK.heartbeat.server!==null){aJ+="&X-Heartbeat-Server="+aK.heartbeat.server
}if(aK.contentType!==""){aJ+="&Content-Type="+(aK.transport==="websocket"?aK.contentType:encodeURIComponent(aK.contentType))
}if(aK.enableProtocol){aJ+="&X-atmo-protocol=true"
}a.util.each(aK.headers,function(aM,aO){var aN=a.util.isFunction(aO)?aO.call(this,aK,aL,at):aO;
if(aN!=null){aJ+="&"+encodeURIComponent(aM)+"="+encodeURIComponent(aN)
}});
return aJ
}function aa(aJ){if(!aJ.isOpen){aJ.isOpen=true;
W("opening",aJ.transport,aJ)
}else{if(aJ.isReopen){aJ.isReopen=false;
W("re-opening",aJ.transport,aJ)
}else{if(at.state==="messageReceived"&&(aJ.transport==="jsonp"||aJ.transport==="long-polling")){aq(at)
}else{return
}}}C(aJ)
}function C(aK){if(aK.heartbeatTimer!=null){clearTimeout(aK.heartbeatTimer)
}if(!isNaN(K)&&K>0){var aJ=function(){if(x("debug")){a.util.debug("Sending heartbeat")
}u(al);
aK.heartbeatTimer=setTimeout(aJ,K)
};
aK.heartbeatTimer=setTimeout(aJ,K)
}}function M(aN){var aK=q;
if((aN!=null)||(typeof(aN)!=="undefined")){aK=aN
}aK.lastIndex=0;
aK.readyState=0;
if((aK.transport==="jsonp")||((aK.enableXDR)&&(a.util.checkCORSSupport()))){aC(aK);
return
}if(a.util.browser.msie&&+a.util.browser.version.split(".")[0]<10){if((aK.transport==="streaming")){if(aK.enableXDR&&window.XDomainRequest){Q(aK)
}else{aB(aK)
}return
}if((aK.enableXDR)&&(window.XDomainRequest)){Q(aK);
return
}}var aM=function(aQ){aK.lastIndex=0;
if(aQ||(aK.reconnect&&az++<aK.maxReconnectOnClose)){at.ffTryingReconnect=true;
W("re-connecting",aN.transport,aN);
ao(aL,aK,aN.reconnectInterval)
}else{U(0,"maxReconnectOnClose reached")
}};
var aO=function(aQ){if(a._beforeUnloadState){a.util.debug(new Date()+" Atmosphere: reconnectF: execution delayed due to _beforeUnloadState flag");
setTimeout(function(){aM(aQ)
},5000)
}else{aM(aQ)
}};
var aJ=function(){at.errorHandled=true;
m();
aO(false)
};
if(aK.force||(aK.reconnect&&(aK.maxRequest===-1||aK.requestCount++<aK.maxRequest))){aK.force=false;
var aL=a.util.xhr();
aL.hasData=false;
N(aL,aK,true);
if(aK.suspend){A=aL
}if(aK.transport!=="polling"){at.transport=aK.transport;
aL.onabort=function(){aI("ajaxrequest.onabort");
af(true)
};
aL.onerror=function(){aI("ajaxrequest.onerror");
at.error=true;
at.ffTryingReconnect=true;
try{at.status=XMLHttpRequest.status
}catch(aQ){at.status=500
}if(!at.status){at.status=500
}if(!at.errorHandled){m();
aO(false)
}}
}aL.onreadystatechange=function(){aI("ajaxRequest.onreadystatechange, new state: "+aL.readyState);
if(aH){aI("onreadystatechange has been ignored due to _abortingConnection flag");
return
}at.error=null;
var aR=false;
var aX=false;
if(aK.transport==="streaming"&&aK.readyState>2&&aL.readyState===4){m();
aO(false);
return
}aK.readyState=aL.readyState;
if(aK.transport==="streaming"&&aL.readyState>=3){aX=true
}else{if(aK.transport==="long-polling"&&aL.readyState===4){aX=true
}}k(q);
if(aK.transport!=="polling"){var aQ=200;
if(aL.readyState===4){aQ=aL.status>1000?0:aL.status
}if(!aK.reconnectOnServerError&&(aQ>=300&&aQ<600)){U(aQ,aL.statusText);
return
}if(aQ>=300||aQ===0){aJ();
return
}if((!aK.enableProtocol||!aN.firstMessage)&&aL.readyState===2){if(a.util.browser.mozilla&&at.ffTryingReconnect){at.ffTryingReconnect=false;
setTimeout(function(){if(!at.ffTryingReconnect){aa(aK)
}},500)
}else{aa(aK)
}}}else{if(aL.readyState===4){aX=true
}}if(aX){var aU=aL.responseText;
at.errorHandled=false;
if(aK.transport==="long-polling"&&a.util.trim(aU).length===0){if(!aL.hasData){aO(true)
}else{aL.hasData=false
}return
}aL.hasData=true;
H(aL,q);
if(aK.transport==="streaming"){if(!a.util.browser.opera){var aT=aU.substring(aK.lastIndex,aU.length);
aR=s(aT,aK,at);
aK.lastIndex=aU.length;
if(aR){return
}}else{a.util.iterate(function(){if(at.status!==500&&aL.responseText.length>aK.lastIndex){try{at.status=aL.status;
at.headers=a.util.parseHeaders(aL.getAllResponseHeaders());
H(aL,q)
}catch(aZ){at.status=404
}k(q);
at.state="messageReceived";
var aY=aL.responseText.substring(aK.lastIndex);
aK.lastIndex=aL.responseText.length;
aR=s(aY,aK,at);
if(!aR){aj()
}if(J(aL,aK)){L(aL,aK);
return
}}else{if(at.status>400){aK.lastIndex=aL.responseText.length;
return false
}}},0)
}}else{aR=s(aU,aK,at)
}var aW=J(aL,aK);
try{at.status=aL.status;
at.headers=a.util.parseHeaders(aL.getAllResponseHeaders());
H(aL,aK)
}catch(aV){at.status=404
}if(aK.suspend){at.state=at.status===0?"closed":"messageReceived"
}else{at.state="messagePublished"
}var aS=!aW&&aN.transport!=="streaming"&&aN.transport!=="polling";
if(aS&&!aK.executeCallbackBeforeReconnect){ao(aL,aK,aK.pollingInterval)
}if(at.responseBody.length!==0&&!aR){aj()
}if(aS&&aK.executeCallbackBeforeReconnect){ao(aL,aK,aK.pollingInterval)
}if(aW){L(aL,aK)
}}};
try{aL.send(aK.data);
v=true
}catch(aP){a.util.log(aK.logLevel,["Unable to connect to "+aK.url]);
U(0,aP)
}}else{if(aK.logLevel==="debug"){a.util.log(aK.logLevel,["Max re-connection reached."])
}U(0,"maxRequest reached")
}}function L(aK,aJ){at.messages=[];
aJ.isReopen=true;
I();
aH=false;
ao(aK,aJ,500)
}function N(aL,aM,aK){var aJ=aM.url;
if(aM.dispatchUrl!=null&&aM.method==="POST"){aJ+=aM.dispatchUrl
}aJ=p(aM,aJ);
aJ=a.util.prepareURL(aJ);
if(aK){aL.open(aM.method,aJ,aM.async);
if(aM.connectTimeout>0){aM.id=setTimeout(function(){if(aM.requestCount===0){m();
l("Connect timeout","closed",200,aM.transport)
}},aM.connectTimeout)
}}if(q.withCredentials&&q.transport!=="websocket"){if("withCredentials" in aL){aL.withCredentials=true
}}if(!q.dropHeaders){aL.setRequestHeader("X-Atmosphere-Framework",c);
aL.setRequestHeader("X-Atmosphere-Transport",aM.transport);
if(aM.heartbeat!==null&&aM.heartbeat.server!==null){aL.setRequestHeader("X-Heartbeat-Server",aL.heartbeat.server)
}if(aM.trackMessageLength){aL.setRequestHeader("X-Atmosphere-TrackMessageSize","true")
}aL.setRequestHeader("X-Atmosphere-tracking-id",aM.uuid);
a.util.each(aM.headers,function(aN,aP){var aO=a.util.isFunction(aP)?aP.call(this,aL,aM,aK,at):aP;
if(aO!=null){aL.setRequestHeader(aN,aO)
}})
}if(aM.contentType!==""){aL.setRequestHeader("Content-Type",aM.contentType)
}}function ao(aK,aL,aM){if(at.closedByClientTimeout){return
}if(aL.reconnect||(aL.suspend&&v)){var aJ=0;
if(aK&&aK.readyState>1){aJ=aK.status>1000?0:aK.status
}at.status=aJ===0?204:aJ;
at.reason=aJ===0?"Server resumed the connection or down.":"OK";
clearTimeout(aL.id);
if(aL.reconnectId){clearTimeout(aL.reconnectId);
delete aL.reconnectId
}if(aM>0){q.reconnectId=setTimeout(function(){M(aL)
},aM)
}else{M(aL)
}}}function r(aJ){aJ.state="re-connecting";
am(aJ)
}function aq(aJ){aJ.state="openAfterResume";
am(aJ);
aJ.state="messageReceived"
}function Q(aJ){if(aJ.transport!=="polling"){o=ad(aJ);
o.open()
}else{ad(aJ).open()
}}function ad(aL){var aK=q;
if((aL!=null)&&(typeof(aL)!=="undefined")){aK=aL
}var aQ=aK.transport;
var aP=0;
var aJ=new window.XDomainRequest();
var aN=function(){if(aK.transport==="long-polling"&&(aK.reconnect&&(aK.maxRequest===-1||aK.requestCount++<aK.maxRequest))){aJ.status=200;
Q(aK)
}};
var aO=aK.rewriteURL||function(aS){var aR=/(?:^|;\s*)(JSESSIONID|PHPSESSID)=([^;]*)/.exec(document.cookie);
switch(aR&&aR[1]){case"JSESSIONID":return aS.replace(/;jsessionid=[^\?]*|(\?)|$/,";jsessionid="+aR[2]+"$1");
case"PHPSESSID":return aS.replace(/\?PHPSESSID=[^&]*&?|\?|$/,"?PHPSESSID="+aR[2]+"&").replace(/&$/,"")
}return aS
};
aJ.onprogress=function(){aM(aJ)
};
aJ.onerror=function(){if(aK.transport!=="polling"){m();
if(az++<aK.maxReconnectOnClose){if(aK.reconnectInterval>0){aK.reconnectId=setTimeout(function(){W("re-connecting",aL.transport,aL);
Q(aK)
},aK.reconnectInterval)
}else{W("re-connecting",aL.transport,aL);
Q(aK)
}}else{U(0,"maxReconnectOnClose reached")
}}};
aJ.onload=function(){};
var aM=function(aR){clearTimeout(aK.id);
var aT=aR.responseText;
aT=aT.substring(aP);
aP+=aT.length;
if(aQ!=="polling"){k(aK);
var aS=s(aT,aK,at);
if(aQ==="long-polling"&&a.util.trim(aT).length===0){return
}if(aK.executeCallbackBeforeReconnect){aN()
}if(!aS){l(at.responseBody,"messageReceived",200,aQ)
}if(!aK.executeCallbackBeforeReconnect){aN()
}}};
return{open:function(){var aR=aK.url;
if(aK.dispatchUrl!=null){aR+=aK.dispatchUrl
}aR=p(aK,aR);
aJ.open(aK.method,aO(aR));
if(aK.method==="GET"){aJ.send()
}else{aJ.send(aK.data)
}if(aK.connectTimeout>0){aK.id=setTimeout(function(){if(aK.requestCount===0){m();
l("Connect timeout","closed",200,aK.transport)
}},aK.connectTimeout)
}},close:function(){aJ.abort()
}}
}function aB(aJ){o=ac(aJ);
o.open()
}function ac(aM){var aL=q;
if((aM!=null)&&(typeof(aM)!=="undefined")){aL=aM
}var aK;
var aN=new window.ActiveXObject("htmlfile");
aN.open();
aN.close();
var aJ=aL.url;
if(aL.dispatchUrl!=null){aJ+=aL.dispatchUrl
}if(aL.transport!=="polling"){at.transport=aL.transport
}return{open:function(){var aO=aN.createElement("iframe");
aJ=p(aL);
if(aL.data!==""){aJ+="&X-Atmosphere-Post-Body="+encodeURIComponent(aL.data)
}aJ=a.util.prepareURL(aJ);
aO.src=aJ;
aN.body.appendChild(aO);
var aP=aO.contentDocument||aO.contentWindow.document;
aK=a.util.iterate(function(){try{if(!aP.firstChild){return
}var aS=aP.body?aP.body.lastChild:aP;
var aU=function(){var aW=aS.cloneNode(true);
aW.appendChild(aP.createTextNode("."));
var aV=aW.innerText;
aV=aV.substring(0,aV.length-1);
return aV
};
if(!aP.body||!aP.body.firstChild||aP.body.firstChild.nodeName.toLowerCase()!=="pre"){var aR=aP.head||aP.getElementsByTagName("head")[0]||aP.documentElement||aP;
var aQ=aP.createElement("script");
aQ.text="document.write('<plaintext>')";
aR.insertBefore(aQ,aR.firstChild);
aR.removeChild(aQ);
aS=aP.body.lastChild
}if(aL.closed){aL.isReopen=true
}aK=a.util.iterate(function(){var aW=aU();
if(aW.length>aL.lastIndex){k(q);
at.status=200;
at.error=null;
aS.innerText="";
var aV=s(aW,aL,at);
if(aV){return""
}l(at.responseBody,"messageReceived",200,aL.transport)
}aL.lastIndex=0;
if(aP.readyState==="complete"){af(true);
W("re-connecting",aL.transport,aL);
if(aL.reconnectInterval>0){aL.reconnectId=setTimeout(function(){aB(aL)
},aL.reconnectInterval)
}else{aB(aL)
}return false
}},null);
return false
}catch(aT){at.error=true;
W("re-connecting",aL.transport,aL);
if(az++<aL.maxReconnectOnClose){if(aL.reconnectInterval>0){aL.reconnectId=setTimeout(function(){aB(aL)
},aL.reconnectInterval)
}else{aB(aL)
}}else{U(0,"maxReconnectOnClose reached")
}aN.execCommand("Stop");
aN.close();
return false
}})
},close:function(){if(aK){aK()
}aN.execCommand("Stop");
af(true)
}}
}function u(aJ){if(ay!=null){F(aJ)
}else{if(A!=null||ah!=null){P(aJ)
}else{if(o!=null){j(aJ)
}else{if(Y!=null){B(aJ)
}else{if(ax!=null){X(aJ)
}else{U(0,"No suspended connection available");
a.util.error("No suspended connection available. Make sure atmosphere.subscribe has been called and request.onOpen invoked before trying to push data")
}}}}}}function an(aK,aJ){if(!aJ){aJ=y(aK)
}aJ.transport="polling";
aJ.method="GET";
aJ.withCredentials=false;
aJ.reconnect=false;
aJ.force=true;
aJ.suspend=false;
aJ.timeout=1000;
M(aJ)
}function F(aJ){ay.send(aJ)
}function ar(aK){if(aK.length===0){return
}try{if(ay){ay.localSend(aK)
}else{if(i){i.signal("localMessage",a.util.stringifyJSON({id:S,event:aK}))
}}}catch(aJ){a.util.error(aJ)
}}function P(aK){var aJ=y(aK);
M(aJ)
}function j(aK){if(q.enableXDR&&a.util.checkCORSSupport()){var aJ=y(aK);
aJ.reconnect=false;
aC(aJ)
}else{P(aK)
}}function B(aJ){P(aJ)
}function O(aJ){var aK=aJ;
if(typeof(aK)==="object"){aK=aJ.data
}return aK
}function y(aK){var aL=O(aK);
var aJ={connected:false,timeout:60000,method:"POST",url:q.url,contentType:q.contentType,headers:q.headers,reconnect:true,callback:null,data:aL,suspend:false,maxRequest:-1,logLevel:"info",requestCount:0,withCredentials:q.withCredentials,async:q.async,transport:"polling",isOpen:true,attachHeadersAsQueryString:true,enableXDR:q.enableXDR,uuid:q.uuid,dispatchUrl:q.dispatchUrl,enableProtocol:false,messageDelimiter:"|",trackMessageLength:q.trackMessageLength,maxReconnectOnClose:q.maxReconnectOnClose,heartbeatTimer:q.heartbeatTimer,heartbeat:q.heartbeat};
if(typeof(aK)==="object"){aJ=a.util.extend(aJ,aK)
}return aJ
}function X(aJ){var aM=a.util.isBinary(aJ)?aJ:O(aJ);
var aK;
try{if(q.dispatchUrl!=null){aK=q.webSocketPathDelimiter+q.dispatchUrl+q.webSocketPathDelimiter+aM
}else{aK=aM
}if(!ax.canSendMessage){a.util.error("WebSocket not connected.");
return
}ax.send(aK)
}catch(aL){ax.onclose=function(aN){};
m();
aA("Websocket failed. Downgrading to "+q.fallbackTransport+" and resending "+aJ);
P(aJ)
}}function G(aK){var aJ=a.util.parseJSON(aK);
if(aJ.id!==S){if(typeof(q.onLocalMessage)!=="undefined"){q.onLocalMessage(aJ.event)
}else{if(typeof(a.util.onLocalMessage)!=="undefined"){a.util.onLocalMessage(aJ.event)
}}}}function l(aM,aJ,aK,aL){at.responseBody=aM;
at.transport=aL;
at.status=aK;
at.state=aJ;
aj()
}function H(aJ,aL){if(!aL.readResponsesHeaders){if(!aL.enableProtocol){aL.uuid=S
}}else{try{var aK=aJ.getResponseHeader("X-Atmosphere-tracking-id");
if(aK&&aK!=null){aL.uuid=aK.split(" ").pop()
}}catch(aM){}}}function am(aJ){n(aJ,q);
n(aJ,a.util)
}function n(aK,aL){switch(aK.state){case"messageReceived":aI("Firing onMessage");
az=0;
if(typeof(aL.onMessage)!=="undefined"){aL.onMessage(aK)
}if(typeof(aL.onmessage)!=="undefined"){aL.onmessage(aK)
}break;
case"error":var aM=(typeof(aK.reasonPhrase)!="undefined")?aK.reasonPhrase:"n/a";
aI("Firing onError, reasonPhrase: "+aM);
if(typeof(aL.onError)!=="undefined"){aL.onError(aK)
}if(typeof(aL.onerror)!=="undefined"){aL.onerror(aK)
}break;
case"opening":delete q.closed;
aI("Firing onOpen");
if(typeof(aL.onOpen)!=="undefined"){aL.onOpen(aK)
}if(typeof(aL.onopen)!=="undefined"){aL.onopen(aK)
}break;
case"messagePublished":aI("Firing messagePublished");
if(typeof(aL.onMessagePublished)!=="undefined"){aL.onMessagePublished(aK)
}break;
case"re-connecting":aI("Firing onReconnect");
if(typeof(aL.onReconnect)!=="undefined"){aL.onReconnect(q,aK)
}break;
case"closedByClient":aI("Firing closedByClient");
if(typeof(aL.onClientTimeout)!=="undefined"){aL.onClientTimeout(q)
}break;
case"re-opening":delete q.closed;
aI("Firing onReopen");
if(typeof(aL.onReopen)!=="undefined"){aL.onReopen(q,aK)
}break;
case"fail-to-reconnect":aI("Firing onFailureToReconnect");
if(typeof(aL.onFailureToReconnect)!=="undefined"){aL.onFailureToReconnect(q,aK)
}break;
case"unsubscribe":case"closed":var aJ=typeof(q.closed)!=="undefined"?q.closed:false;
if(!aJ){aI("Firing onClose ("+aK.state+" case)");
if(typeof(aL.onClose)!=="undefined"){aL.onClose(aK)
}if(typeof(aL.onclose)!=="undefined"){aL.onclose(aK)
}}else{aI("Request already closed, not firing onClose ("+aK.state+" case)")
}q.closed=true;
break;
case"openAfterResume":if(typeof(aL.onOpenAfterResume)!=="undefined"){aL.onOpenAfterResume(q)
}break
}}function af(aJ){if(at.state!=="closed"){at.state="closed";
at.responseBody="";
at.messages=[];
at.status=!aJ?501:200;
aj()
}}function aj(){var aL=function(aO,aP){aP(at)
};
if(ay==null&&R!=null){R(at.responseBody)
}q.reconnect=q.mrequest;
var aJ=typeof(at.responseBody)==="string";
var aM=(aJ&&q.trackMessageLength)?(at.messages.length>0?at.messages:[""]):new Array(at.responseBody);
for(var aK=0;
aK<aM.length;
aK++){if(aM.length>1&&aM[aK].length===0){continue
}at.responseBody=(aJ)?a.util.trim(aM[aK]):aM[aK];
if(ay==null&&R!=null){R(at.responseBody)
}if((at.responseBody.length===0||(aJ&&al===at.responseBody))&&at.state==="messageReceived"){continue
}am(at);
if(g.length>0){if(x("debug")){a.util.debug("Invoking "+g.length+" global callbacks: "+at.state)
}try{a.util.each(g,aL)
}catch(aN){a.util.log(q.logLevel,["Callback exception"+aN])
}}if(typeof(q.callback)==="function"){if(x("debug")){a.util.debug("Invoking request callbacks")
}try{q.callback(at)
}catch(aN){a.util.log(q.logLevel,["Callback exception"+aN])
}}}}this.subscribe=function(aJ){ap(aJ);
ab()
};
this.execute=function(){ab()
};
this.close=function(){I()
};
this.disconnect=function(){E()
};
this.getUrl=function(){return q.url
};
this.push=function(aL,aK){if(aK!=null){var aJ=q.dispatchUrl;
q.dispatchUrl=aK;
u(aL);
q.dispatchUrl=aJ
}else{u(aL)
}};
this.getUUID=function(){return q.uuid
};
this.pushLocal=function(aJ){ar(aJ)
};
this.enableProtocol=function(aJ){return q.enableProtocol
};
this.init=function(){ak()
};
this.request=q;
this.response=at
}};
a.subscribe=function(i,l,k){if(typeof(l)==="function"){a.addCallback(l)
}if(typeof(i)!=="string"){k=i
}else{k.url=i
}f=((typeof(k)!=="undefined")&&typeof(k.uuid)!=="undefined")?k.uuid:0;
var j=new a.AtmosphereRequest(k);
j.execute();
h[h.length]=j;
return j
};
a.unsubscribe=function(){if(h.length>0){var j=[].concat(h);
for(var l=0;
l<j.length;
l++){var k=j[l];
k.close();
clearTimeout(k.response.request.id);
if(k.heartbeatTimer){clearTimeout(k.heartbeatTimer)
}}}h=[];
g=[]
};
a.unsubscribeUrl=function(k){var j=-1;
if(h.length>0){for(var m=0;
m<h.length;
m++){var l=h[m];
if(l.getUrl()===k){l.close();
clearTimeout(l.response.request.id);
if(l.heartbeatTimer){clearTimeout(l.heartbeatTimer)
}j=m;
break
}}}if(j>=0){h.splice(j,1)
}};
a.addCallback=function(i){if(a.util.inArray(i,g)===-1){g.push(i)
}};
a.removeCallback=function(j){var i=a.util.inArray(j,g);
if(i!==-1){g.splice(i,1)
}};
a.util={browser:{},parseHeaders:function(j){var i,l=/^(.*?):[ \t]*([^\r\n]*)\r?$/mg,k={};
while(i=l.exec(j)){k[i[1]]=i[2]
}return k
},now:function(){return new Date().getTime()
},isArray:function(i){return Object.prototype.toString.call(i)==="[object Array]"
},inArray:function(l,m){if(!Array.prototype.indexOf){var j=m.length;
for(var k=0;
k<j;
++k){if(m[k]===l){return k
}}return -1
}return m.indexOf(l)
},isBinary:function(i){return/^\[object\s(?:Blob|ArrayBuffer|.+Array)\]$/.test(Object.prototype.toString.call(i))
},isFunction:function(i){return Object.prototype.toString.call(i)==="[object Function]"
},getAbsoluteURL:function(i){var j=document.createElement("div");
j.innerHTML='<a href="'+i+'"/>';
return encodeURI(decodeURI(j.firstChild.href))
},prepareURL:function(j){var k=a.util.now();
var i=j.replace(/([?&])_=[^&]*/,"$1_="+k);
return i+(i===j?(/\?/.test(j)?"&":"?")+"_="+k:"")
},trim:function(i){if(!String.prototype.trim){return i.toString().replace(/(?:(?:^|\n)\s+|\s+(?:$|\n))/g,"").replace(/\s+/g," ")
}else{return i.toString().trim()
}},param:function(m){var k,i=[];
function l(n,o){o=a.util.isFunction(o)?o():(o==null?"":o);
i.push(encodeURIComponent(n)+"="+encodeURIComponent(o))
}function j(o,p){var n;
if(a.util.isArray(p)){a.util.each(p,function(r,q){if(/\[\]$/.test(o)){l(o,q)
}else{j(o+"["+(typeof q==="object"?r:"")+"]",q)
}})
}else{if(Object.prototype.toString.call(p)==="[object Object]"){for(n in p){j(o+"["+n+"]",p[n])
}}else{l(o,p)
}}}for(k in m){j(k,m[k])
}return i.join("&").replace(/%20/g,"+")
},storage:function(){try{return !!(window.localStorage&&window.StorageEvent)
}catch(i){return false
}},iterate:function(k,j){var l;
j=j||0;
(function i(){l=setTimeout(function(){if(k()===false){return
}i()
},j)
})();
return function(){clearTimeout(l)
}
},each:function(o,p,k){if(!o){return
}var n,l=0,m=o.length,j=a.util.isArray(o);
if(k){if(j){for(;
l<m;
l++){n=p.apply(o[l],k);
if(n===false){break
}}}else{for(l in o){n=p.apply(o[l],k);
if(n===false){break
}}}}else{if(j){for(;
l<m;
l++){n=p.call(o[l],l,o[l]);
if(n===false){break
}}}else{for(l in o){n=p.call(o[l],l,o[l]);
if(n===false){break
}}}}return o
},extend:function(m){var l,k,j;
for(l=1;
l<arguments.length;
l++){if((k=arguments[l])!=null){for(j in k){m[j]=k[j]
}}}return m
},on:function(k,j,i){if(k.addEventListener){k.addEventListener(j,i,false)
}else{if(k.attachEvent){k.attachEvent("on"+j,i)
}}},off:function(k,j,i){if(k.removeEventListener){k.removeEventListener(j,i,false)
}else{if(k.detachEvent){k.detachEvent("on"+j,i)
}}},log:function(k,j){if(window.console){var i=window.console[k];
if(typeof i==="function"){i.apply(window.console,j)
}}},warn:function(){a.util.log("warn",arguments)
},info:function(){a.util.log("info",arguments)
},debug:function(){a.util.log("debug",arguments)
},error:function(){a.util.log("error",arguments)
},xhr:function(){try{return new window.XMLHttpRequest()
}catch(j){try{return new window.ActiveXObject("Microsoft.XMLHTTP")
}catch(i){}}},parseJSON:function(i){return !i?null:window.JSON&&window.JSON.parse?window.JSON.parse(i):new Function("return "+i)()
},stringifyJSON:function(k){var n=/[\\\"\x00-\x1f\x7f-\x9f\u00ad\u0600-\u0604\u070f\u17b4\u17b5\u200c-\u200f\u2028-\u202f\u2060-\u206f\ufeff\ufff0-\uffff]/g,l={"\b":"\\b","\t":"\\t","\n":"\\n","\f":"\\f","\r":"\\r",'"':'\\"',"\\":"\\\\"};
function i(o){return'"'+o.replace(n,function(p){var q=l[p];
return typeof q==="string"?q:"\\u"+("0000"+p.charCodeAt(0).toString(16)).slice(-4)
})+'"'
}function j(o){return o<10?"0"+o:o
}return window.JSON&&window.JSON.stringify?window.JSON.stringify(k):(function m(t,s){var r,q,o,p,w=s[t],u=typeof w;
if(w&&typeof w==="object"&&typeof w.toJSON==="function"){w=w.toJSON(t);
u=typeof w
}switch(u){case"string":return i(w);
case"number":return isFinite(w)?String(w):"null";
case"boolean":return String(w);
case"object":if(!w){return"null"
}switch(Object.prototype.toString.call(w)){case"[object Date]":return isFinite(w.valueOf())?'"'+w.getUTCFullYear()+"-"+j(w.getUTCMonth()+1)+"-"+j(w.getUTCDate())+"T"+j(w.getUTCHours())+":"+j(w.getUTCMinutes())+":"+j(w.getUTCSeconds())+'Z"':"null";
case"[object Array]":o=w.length;
p=[];
for(r=0;
r<o;
r++){p.push(m(r,w)||"null")
}return"["+p.join(",")+"]";
default:p=[];
for(r in w){if(b.call(w,r)){q=m(r,w);
if(q){p.push(i(r)+":"+q)
}}}return"{"+p.join(",")+"}"
}}})("",{"":k})
},checkCORSSupport:function(){if(a.util.browser.msie&&!window.XDomainRequest&&+a.util.browser.version.split(".")[0]<11){return true
}else{if(a.util.browser.opera&&+a.util.browser.version.split(".")<12){return true
}else{if(a.util.trim(navigator.userAgent).slice(0,16)==="KreaTVWebKit/531"){return true
}else{if(a.util.trim(navigator.userAgent).slice(-7).toLowerCase()==="kreatel"){return true
}}}}var i=navigator.userAgent.toLowerCase();
var j=i.indexOf("android")>-1;
if(j){return true
}return false
}};
e=a.util.now();
(function(){var j=navigator.userAgent.toLowerCase(),i=/(chrome)[ \/]([\w.]+)/.exec(j)||/(opera)(?:.*version|)[ \/]([\w.]+)/.exec(j)||/(msie) ([\w.]+)/.exec(j)||/(trident)(?:.*? rv:([\w.]+)|)/.exec(j)||j.indexOf("android")<0&&/version\/(.+) (safari)/.exec(j)||j.indexOf("compatible")<0&&/(mozilla)(?:.*? rv:([\w.]+)|)/.exec(j)||[];
if(i[2]==="safari"){i[2]=i[1];
i[1]="safari"
}a.util.browser[i[1]||""]=true;
a.util.browser.version=i[2]||"0";
a.util.browser.vmajor=a.util.browser.version.split(".")[0];
if(a.util.browser.trident){a.util.browser.msie=true
}if(a.util.browser.msie||(a.util.browser.mozilla&&+a.util.browser.version.split(".")[0]===1)){a.util.storage=false
}})();
a.util.on(window,"unload",function(i){a.util.debug(new Date()+" Atmosphere: unload event");
a.unsubscribe()
});
a.util.on(window,"beforeunload",function(i){a.util.debug(new Date()+" Atmosphere: beforeunload event");
a._beforeUnloadState=true;
setTimeout(function(){a.util.debug(new Date()+" Atmosphere: beforeunload event timeout reached. Reset _beforeUnloadState flag");
a._beforeUnloadState=false
},5000)
});
a.util.on(window,"keypress",function(i){if(i.charCode===27||i.keyCode===27){if(i.preventDefault){i.preventDefault()
}}});
a.util.on(window,"offline",function(){a.util.debug(new Date()+" Atmosphere: offline event");
d=true;
if(h.length>0){var j=[].concat(h);
for(var l=0;
l<j.length;
l++){var k=j[l];
k.close();
clearTimeout(k.response.request.id);
if(k.heartbeatTimer){clearTimeout(k.heartbeatTimer)
}}}});
a.util.on(window,"online",function(){a.util.debug(new Date()+" Atmosphere: online event");
if(h.length>0){for(var j=0;
j<h.length;
j++){h[j].init();
h[j].execute()
}}d=false
});
return a
}));