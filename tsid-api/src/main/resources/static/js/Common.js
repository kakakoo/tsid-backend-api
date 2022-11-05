<!--

fontsize = 0;

document.onkeydown=function(e)
{
	if( typeof(e)!="undefined" )
	{
		keypressed(e);
	}
	else
	{
		keypressed();
	}
}
	
function keypressed(e)
{
	if(e==null)
	{
		if( event.keyCode == 123 || event.keyCode == 17 )
		{
			event.returnValue = false;
		}
	}
	else
	{
		if( e.which == 17 )
		{
			e.returnValue = false;
		}
	}
}

function hideURLbar()
{
	setTimeout(function(){ window.scrollTo(0,1); }, 50);
}

function isEnter(e)
{
	if(e==null)
	{
		if( event.keyCode == 13 )
		{
			event.returnValue = false;
			return false;
		}
	}
	else
	{
		if( e.which == 13 )
		{
			e.returnValue = false;
			return false;
		}
	}
}

function doCancel()
{
	var backurl = $('input[name=BackURL]').val();

	if(typeof backurl != "undefined" && backurl != "")
		$('#cancel').submit();
};

function openAgreement( nGuide )
{
	$('#agreement01, #agreement02, #agreement03, #agreement04, #agreeall').attr('disabled', true);
	$('#name,#year,#month,#day,#gender,#nation,#dstaddr').attr('disabled', true);
	$('#agree_layer'+nGuide).show();
};

function openAgreement2( nGuide )
{
	$('#agreement01').attr('disabled', true);
	$('#otp').attr('disabled', true);
	$('#agree_layer'+nGuide).show();
};

function emulAcceptCharset(form)
{
	/*
	 ** For Explore
	 **/
	if( form.canHaveHTML )
	{
		document.charset = form.acceptCharset;
	}

	return true;
}

-->
