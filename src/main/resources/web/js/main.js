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
	loadPanel();
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
		toggleButtons(key,  dscLeds[key]);
	}
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

function toggleButtons(ledName, ledState)
{
	if(ledName === "READY")
	{
		switch(ledState)
		{
			case "ON":
				$('#btnStayarm').show();
			    $('#btnAwayarm').show();
			    $('#btnDisarm').hide();
			    $('#txtUsercode').hide();
			    break;
			default:
				$('#btnStayarm').hide();
			    $('#btnAwayarm').hide();
			    $('#btnDisarm').show();
			    $('#txtUsercode').show();
		}
	}
	if(ledName === "ARM")
	{
		switch(ledState)
		{
			case "ON":
				$('#btnStayarm').hide();
			    $('#btnAwayarm').hide();
			    $('#btnDisarm').show();
			    $('#txtUsercode').show();
			    break;
			default:
				$('#btnStayarm').show();
			    $('#btnAwayarm').show();
			    $('#btnDisarm').hide();
			    $('#txtUsercode').hide();
		}
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

