$(document).ready(function() {
    console.debug("Web is ready");

    // State of the reconnection attempts
    var reconnect = false;

    // Build a formated Tweet to be displayed on the app
    function buildTweetPanel(tweet) {
        var author = $("<b></b>").attr('class','lead text-muted').text('@' + tweet.author);
        var time = $("<span></span>").text(' - ' + parseTwitterDate(tweet.created));

        var panel = $("<div></div>").attr('class', 'panel panel-default');
        var panelHead = $("<div></div>").attr('class', 'panel-heading');
        var panelBody = $("<div></div>").attr('class', 'panel-body').text(tweet.text);

        panelHead.append(author, time);
        panel.append(panelHead, panelBody);

        return panel;
    }

    // Add new Tweet to the app
    function appendTweet(tweet) {
        $("#tweets").prepend(buildTweetPanel(tweet));
    }
    
    // Utility function to parse Twitter's date value into 
    // YYYY/MM/DD HH:MM:SS
    function parseTwitterDate(text) {
        var d = new Date(Date.parse(text.replace(/( +)/, ' UTC$1')));
        return "" + d.getFullYear() + "/" + (d.getMonth() + 1) + "/" + d.getDate() + 
               " " + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds();
    }

    // Function that connects to the server's web socket
    function connect(attempt) {
        console.debug("Starting connection");
        console.debug("Url: ", url);
        
        // Count the attempt and create a new JS WebSocket object with the 
        // URL of the service.
        var connectionAttempt = attempt;
        var tweetSocket = new WebSocket(url);

        // On every new message from the server (new Tweet) parse the JSON
        // object and add (display) into the app
        tweetSocket.onmessage = function(evt) {
            console.debug(evt);

            var tweet = JSON.parse(evt.data);
            appendTweet(tweet);
        };

        // When the connection is open (we set as first attempt), sends a subscribe 
        // message to the server to start communication.
        tweetSocket.onopen = function() {
            connectionAttempt = 1;
            tweetSocket.send("subscribe");
            if(reconnect) {
                $.notify({
                    message: "Server connection recovered."
                }, {
                    type: "success"
                });
            }
        };

        // When the communication is closed a new reconnection process is started
        // after 10 attempts reconnection process is cancelled.
        tweetSocket.onclose = function() {
            if(connectionAttempt <= 10) {
                $.notify({
                    message: "WARNING: Lost server connection, attempting to reconnect. Attempt number " + connectionAttempt
                }, {
                    type: "warning"
                });

                setTimeout(function() {
                    reconnect = true;
                    connect(connectionAttempt + 1);
                }, 5000);
            } else {
                $.notify({
                    message: "The connection with the server was lost."
                }, {
                    type: "danger"
                });
            }
        };
    }

    // Start process to establish a new connection with the server.
    connect(1);
});