$( document ).on( "pagecreate", "#dsc-page", function() {

	$( document ).on( "swipeleft swiperight", "#dsc-page", function( e ) {
		// We check if there is no open panel on the page because otherwise
		// a swipe to close the left panel would also open the right panel (and v.v.).
		// We do this by checking the data that the framework stores on the page element (panel: open).
		if ( $( ".ui-page-active" ).jqmData( "panel" ) !== "open" ) {
			if ( e.type === "swipeleft" ) {
				$( "#right-panel" ).panel( "open" );
			} else if ( e.type === "swiperight" ) {
				$( "#left-panel" ).panel( "open" );
			}
		}
	});
});

$('#btnStayarm').click(function() {
	stayArm();
	return false;
});

$('#btnAwayarm').click(function() {
	awayArm();
	return false;
});

$('#btnDisarm').click(function() {
    var code = $("#txtUsercode").val();

    if (code.length > 0 && code.match(/[0-9]{4,6}/)) 
    {
	    $("#spanMsgUserCode").html('');
	    $("#txtUsercode").val('');
	    disArm(code);
	}
	else
	{
	    $("#spanMsgUserCode").html('Please provide a valid user code');
	}
	return false;
});

 
$('#btnClearEvents').click(function() {
	$('#lsEvents').empty();
	return false;
});

$('#btnRefresh').click(function() {
	refresh();
	return false;
});

$('#btnFire').click(function() {
	confirmPanicDialog('FIRE');
	return false;
});

$('#btnAmb').click(function() {
	confirmPanicDialog('AMBULANCE');
	return false;
	});

$('#btnPol').click(function() {
	confirmPanicDialog('POLICE');
	return false;
	});

$('#btnStayarm').hide();
$('#btnAwayarm').hide();
$('#btnDisarm').hide();
$('#txtUsercode').hide();

loadPanel();

var eventsWs = wsConnect("events");

loadCams();

$('#lsCams').on("click", "a", function() {
	
	var id = $(this).attr('id');
	
	cameraPopup(id);
	
	return false;
	});

$('#btnCamArmAll').click(function() {
	$.ajax({
		type: 'POST',
		url: '/api/foscam/arm',
		dataType: "json",
		success: onArmSuccess,
		error: onArmFailure
	});
	return false;
	});

$('#btnCamDisarmAll').click(function() {
	$.ajax({
		type: 'POST',
		url: '/api/foscam/disarm',
		dataType: "json",
		success: onDisArmSuccess,
		error: onDisArmFailure
	});
	return false;
	});

function loadLeds()
{
	$.ajax({
		type: 'GET',
		url: '/api/dsc/leds',
		dataType: "json",
		success: renderLeds,
		error: onLoadFailure
	});	
}

function loadAlarms()
{
	$.ajax({
		type: 'GET',
		url: '/api/dsc/alarms',
		dataType: "json",
		success: renderAlarms,
		error: onLoadFailure
	});
}

function loadTroubles()
{
	$.ajax({
		type: 'GET',
		url: '/api/dsc/troubles',
		dataType: "json",
		success: renderTroubles,
		error: onLoadFailure
	});
}

function loadPanel() {
	console.log('loadPanel ' + location.protocol + '//' + location.host );
	loadLeds();
	loadAlarms();
	loadTroubles();
}

function renderLeds(dscLeds) 
{
	for(var key in dscLeds)
	{
		if($('#led' + key).length)
		{
			console.log('Updating led ' + key);
			toggleLed($('#led' + key), getLedClass(key, dscLeds[key]));
		}
		else
		{
			console.log('Unknown led ' + key + '=' + dscLeds[key]);
		}
	}
	toggleButtons(dscLeds);
}

function renderAlarms(dscAlarms) 
{
	$('#alarmsRow').empty();
	if(dscAlarms.length > 0)
	{
		var even = false;
		
		$('#alarmsRow').append("<td>Alarms:</td>")
		dscAlarms.forEach(function(alarm)
				{
					if(!even)
					{
						$('#alarmsRow').append("<td>" + alarm + "</td>")
						even = true;
					}
					else
					{
						$('#alarmsRow').append("<td class='info-td-even'>" + alarm + "</td>")
						even = false;
					}
				});
	}
}

function renderTroubles(dscTroubles) 
{
	$('#troublesRow').empty();
	if(dscTroubles.length > 0)
	{
		$('#troublesRow').append("<td>Troubles:</td>")
		dscTroubles.forEach(function(trouble)
				{
					$('#troublesRow').append("<td>" + trouble + "</td>")
				});
	}
}

function stayArm()
{
	console.log('stayArm');
	$.ajax({
		type: 'POST',
		url: '/api/dsc/stayarm',
		success: onArmSuccess,
		error: onArmFailure
	});
}

function awayArm()
{
	console.log('awayArm');
	$.ajax({
		type: 'POST',
		url: '/api/dsc/awayarm',
		success: onArmSuccess,
		error: onArmFailure
	});
}

function disArm(code)
{
	console.log('disArm');
	$.ajax({
		type: 'POST',
		url: '/api/dsc/disarm',
		data: code,
		success: onDisArmSuccess,
		error: onDisArmFailure
	});
}

function onArmSuccess(data)
{
	setMsg("Arming in progress");
}

function onArmFailure(jqXHR, textStatus, errorThrown)
{
    alert('failed to arm: ' + textStatus);
}

function onLoadFailure(jqXHR, textStatus, errorThrown)
{
    alert('failed to load: ' + textStatus);
}

function onDisArmSuccess(data)
{
	setMsg("Disarming in progress");
}

function onDisArmFailure(jqXHR, textStatus, errorThrown)
{
    alert('failed to disarm: ' + textStatus);
}

function toggleLed(led, classNm)
{
    led.removeClass();
    led.addClass(classNm);
}

function getLedClass(key, ledState)
{
	if(ledState === "OFF") return "led-off";
	
	if(key === "READY")
	{
		if(ledState === "ON") return "led-green";
		else return "led-green-blink";
	}
	if(key === "ARMED")
	{
		if(ledState === "ON") return "led-red";
		else return "led-red-blink";
	}
	if(key === "TROUBLE")
	{
		return "led-red-blink";
	}
	if(key === "PROGRAM")
	{
		return "led-green-blink";
	}
	return "led-yellow";
}

function toggleButtons(dscLeds)
{
	var armed = false;
	var ready = false;
	
	for(var ledName in dscLeds)
	{
		if(ledName === "ARMED" && dscLeds[ledName] === "ON")
		{
			armed = true;
		}
		if(ledName === "READY" && dscLeds[ledName] === "ON")
		{
			ready = true;
		}
	}
	
	if(armed)
	{
		$('#btnStayarm').hide();
	    $('#btnAwayarm').hide();
	    $('#btnDisarm').show();
	    $('#txtUsercode').show();
	}
	else if(ready)
	{
		$('#btnStayarm').show();
	    $('#btnAwayarm').show();
	    $('#btnDisarm').hide();
	    $('#txtUsercode').hide();
	}
	else
	{
		$('#btnStayarm').hide();
	    $('#btnAwayarm').hide();
	    $('#btnDisarm').hide();
	    $('#txtUsercode').hide();
	}
}

function wsConnect(svcNm) 
{
	// Create a websocket
	var webSocket = new WebSocket("ws://" + location.host + "/ws/" + svcNm);

	webSocket.onopen = function(event) {
		updateOutput("Connected!");

	};

	webSocket.onmessage = function(event) {
		updateEvent(event.data);
	};

	webSocket.onclose = function(event) {
		updateOutput("Connection Closed");
	};
	
	return webSocket;
}

function updateOutput(msg)
{
	console.log(msg);
}

function setMsg(msg)
{
    $('#spanPanelMsg').html(msg);
}

function updateEvent(msg)
{
	console.log(msg);
	var mo = JSON.parse(msg)
	if($("#lsEvents li").length > 100) 
	{
		$('#lsEvents').empty();
	}
	
	$('#lsEvents').prepend("<li><p><small>" + mo.ts + "</small></p><p>" + mo.msg +"</p></li>")
	switch(mo.type)
	{
		case "ARM":
		case "LED":
		case "MISC":
			loadLeds();
			setMsg(mo.msg);
			break;
		case "TROUBLE":
			loadLeds();
			loadTroubles();
			setMsg(mo.msg);
			break;
		case "ALARM":
			loadLeds();
			loadAlarms();
			setMsg(mo.msg);
			break;
		default:
			console.log("Ignoring message of type " + mo.type);
	}
}

//http://jsfiddle.net/EELLj/2/
function confirmDialog(text, callback) {
    var popupDialogId = 'confirmationPopupDialog';
    $('<div data-role="popup" id="' + popupDialogId + '" data-confirmed="no" data-transition="pop" data-overlay-theme="b" data-theme="b" data-dismissible="false" style="max-width:400px;"> \
                        <div data-role="header" data-theme="a">\
                            <h1>Confirmation</h1>\
                        </div>\
                        <div role="main" class="ui-content">\
                            <h3 class="ui-title">' + text + '</h3>\
                            <a href="#" class="ui-btn ui-corner-all ui-shadow ui-btn-inline ui-btn-b optionConfirm" data-rel="back">Yes</a>\
                            <a href="#" class="ui-btn ui-corner-all ui-shadow ui-btn-inline ui-btn-b optionCancel" data-rel="back" data-transition="flow">No</a>\
                        </div>\
                    </div>')
        .appendTo($.mobile.pageContainer);
    var popupDialogObj = $('#' + popupDialogId);
    popupDialogObj.trigger('create');
    popupDialogObj.popup({
        afterclose: function (event, ui) {
            popupDialogObj.find(".optionConfirm").first().off('click');
            var isConfirmed = popupDialogObj.attr('data-confirmed') === 'yes' ? true : false;
            $(event.target).remove();
            if (isConfirmed && callback) {
                callback();
            }
        }
    });
    popupDialogObj.popup('open');
    popupDialogObj.find(".optionConfirm").first().on('click', function () {
        popupDialogObj.attr('data-confirmed', 'yes');
    });
}

function confirmPanicDialog(panic)
{
	confirmDialog("Trigger " + panic + " alarm?", 
			function()
			{
				sendPanic(panic);
			});	
}

function sendPanic(panic) {
	console.log('triggering panic: ' + panic);
	$.ajax({
		type: 'POST',
		url: '/api/dsc/panic',
		data: panic,
		success: loadPanel,
		error: onPanicFailure
	});
}

function onPanicFailure(jqXHR, textStatus, errorThrown)
{
    alert('failed to panic: ' + textStatus);
}

function refresh()
{
	$.ajax({
		type: 'POST',
		url: '/api/dsc/refresh',
		dataType: 'text',
		success: loadPanel,
		error: onLoadFailure
	});
}

function loadCams()
{
	$.ajax({
		type: 'GET',
		url: '/api/foscam/names',
		dataType: "json",
		success: listCams,
		error: onLoadFailure
	});
}

function listCams(cams) 
{
	$('#lsCams').empty();
	cams.forEach(function(cam)
			{
				$('#lsCams').append('<li data-icon="eye"><a href="#" id="' + cam + '"><h2>' + cam + '</h2></a></li>');
			});
}


function cameraPopup(cam) {
    var popupDialogId = 'cameraPopup';
    $('<div data-role="popup" id="' + popupDialogId + '" data-theme="c">\
    	<div class="ui-block-a">\
	        <ul id="popupActionList" data-role="listview" data-inset="true" style="min-width:210px;">\
	            <li data-role="list-divider">Choose an action</li>\
	            <li id="camStatus" data-icon="info"><a href="#"  data-rel="back">Status</a></li>\
	            <li id="camArm" data-icon="lock"><a href="#"  data-rel="back">Arm</a></li>\
	            <li id="camDisarm" data-icon="action"><a href="#"	 data-rel="back">Disarm</a></li>\
	            <li id="camSnap" data-icon="camera"><a href="#"  data-rel="back">Snap</a></li>\
    			<li id="camRecord" data-icon="cloud"><a href="#"  data-rel="back">Record</a></li>\
    			<li id="camStream" data-icon="video"><a href="#"  data-rel="back">Stream</a></li>\
	        </ul>\
    	</div>\
       </div>')
        .appendTo($.mobile.activePage);
    var popupDialogObj = $('#' + popupDialogId);
    var popupActionListObj= $('#' + 'popupActionList');
    
    popupDialogObj.trigger('create');
    popupDialogObj.popup({
        afterclose: function (event, ui) {
            $(event.target).remove();
        }
    });
    $('#popupActionList').on('click', 'li', function() {
        window[$(this).attr('id')](cam); 
    });
    popupDialogObj.popup('open');
}

function camStatus(cam)
{
	$.ajax({
		type: 'GET',
		url: '/api/foscam/' + cam + '/status',
		dataType: "json",
		success: showCamStatus,
		error: onLoadFailure
	});
	return false;
}

function camArm(cam)
{
	$.ajax({
		type: 'POST',
		url: '/api/foscam/' + cam + '/arm',
		success: onArmSuccess,
		error: onArmFailure
	});
	return false;
}

function camDisarm(cam)
{
	$.ajax({
		type: 'POST',
		url: '/api/foscam/' + cam + '/disarm',
		success: onDisArmSuccess,
		error: onDisArmFailure
	});
	return false;
}

function camSnap(cam)
{
	var popupDialogId = 'popupCamSnap';
	
    $('<div data-role="popup" id="' +  popupDialogId + '" data-overlay-theme="b" data-theme="b" data-corners="false">\
    	<a href="#" data-rel="back" class="ui-btn ui-corner-all ui-shadow ui-btn-a ui-icon-delete ui-btn-icon-notext ui-btn-right">Close</a>\
    	<img src="/api/foscam/' + cam + '/snapPicture" style="max-height:512px;" alt="Image from camera ' + cam + '"/>\
       </div>')
       .appendTo($.mobile.activePage);
   var popupDialogObj = $('#' + popupDialogId);
   popupDialogObj.trigger('create');
   popupDialogObj.popup({
       afterclose: function (event, ui) {
           $(event.target).remove();
       }
   });
   setTimeout(function() { popupDialogObj.popup('open'); }, 500);
   return false;
}

function showCamStatus(data)
{
	alert(JSON.stringify(data));
}

function camRecord(cam)
{
	$.ajax({
		type: 'POST',
		url: '/api/foscam/' + cam + '/record',
		success: function(data){
			alert("Started recording " + cam);
		},
		error: onLoadFailure
	});
	return false;
}

function camStream(cam)
{
     var win = window.open('/api/foscam/' + cam + '/stream', '_blank');
     win.focus();
    return false;
}

$('#btnCamRecordAll').click(function() {
	$.ajax({
		type: 'POST',
		url: '/api/foscam/record',
		success: function(data){
			alert("Started recording all cameras");
		},
		error: onLoadFailure
	});
	return false;
	});
