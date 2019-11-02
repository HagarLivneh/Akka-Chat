package Client;

import akka.actor.*;
import akka.pattern.AskableActorSelection;
import akka.pattern.Patterns;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.*;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import Messages.Message.*;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import static java.util.concurrent.TimeUnit.SECONDS;


public class Client
{
    ActorSystem system;
    ActorRef clientActor;
    ActorRef inviterActor;
    String userName;
    boolean responseMode;

    public static void main(String[] args)
    {
        Client c = new Client();
        Scanner myObj = new Scanner(System.in);
        String input;

        for (;;)
        {//input loop
            input= myObj.nextLine();

            if(!c.responseMode)
                c.handleInput(input);
            else
                c.userResponse(input);
        }

    }

    public Client()
    {//Construcror - init client's mode and inviterActor
        this.responseMode = false;
        this.inviterActor = null;
    }

    public void setResponseMode(boolean mode)
    {//Updates the client mode: true- answer to invitation, false- regular message input
        this.responseMode = mode;
    }

    public void setInviterActor(ActorRef inviter)
    {//Updates the client's inviter
        this.inviterActor = inviter;
    }

    public void userResponse(String text)
    {//Send a message with the client's response to a group invitation to the inviter
        setResponseMode(false);
        ResponseGroupInvMessage response = new ResponseGroupInvMessage();
        response.text = text;
        response.userInviterActor = inviterActor;
        setInviterActor(null);
        clientActor.tell(response, ActorRef.noSender());
    }

    public void handleInput(String text)
    {//Get the input line, handle a connection request or else
     //tell the client's actor the input line
        if(text.startsWith("/user connect"))
        {//First connection
            String[] inputParams = text.split(" ");
            if(inputParams.length<3)
                System.out.println("Not a valid input, try again!");
            else
                {
                this.userName = inputParams[2];
                //Init the client's connection configuration
                Config configWithPort = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + 0).withFallback(ConfigFactory.load());
                ConfigFactory.invalidateCaches();
                system = ActorSystem.create("RemoteClient", configWithPort);
                //Create an ActorRef for the client
                clientActor = system.actorOf(Props.create(ClientActor.class, this.userName, this), "Client");
                }
        }
        else
        {//Connection already exists in this case.
        //Tell the client's actor the input message
            UserInputMessage msg = new UserInputMessage();
            msg.text = text;
            if(clientActor != null)
                clientActor.tell(msg, ActorRef.noSender());
            else//connection us not available- server is offline
                System.out.println("server is offline! try again later!");
        }
    }

    public static class ClientActor extends AbstractActor
    {
        //Client mode options (in group): admin/co-admin/user/muted
        public String serverPath = "akka.tcp://System@127.0.0.1:8000/user/Server";

        String userName;
        Client client;
        ActorSelection serverActor;
        //Map of groupName-previledge in group, of the client, , for every group which includes the client.
        HashMap<String, Previledges> GroupsPreviledges = new HashMap<String, Previledges>();
        //Map of groupName-group actor, for every group which includes the client.
        HashMap<String, ActorRef> GroupsInformation = new HashMap<String, ActorRef>();


        public ClientActor(String userName, Client client)
       {//Constructor- init the user name and it's client instance(of Client class)
              this.userName = userName;
              this.client = client;
        }

        @Override
        public Receive createReceive()
        {//Handle incoming messages of the ClientActor's mailbox
            return receiveBuilder()
                    .match(UserInputMessage.class, msg ->
                    {//Get user input and call handleInput function to handle it.
                        handleInput(msg.text);
                    })
                    .match(ConnectRequestMessage.class, msg ->
                    {//Get connection message from the server which inits the server
                        serverActor = msg.server;
                        if (serverActor != null) {
                            printText(userName + " has connected successfully!",false);
                        }
                    }).match(PrivateUserTextMessage.class, msg ->
                    {//Get private message and prints it in private message format
                        if (serverActor != null) {
                            printText("[user][" + msg.sender + "]" + msg.text, true);
                        }
                    })
                     .match(PrivateFileMessage.class, msg ->
                     {//Get private file message, save it and print a relevant message
                        if (serverActor != null)
                        {
                            String time_str = LocalTime.now().toString();
                            String fileNameToSave = "FROM_" + msg.sender + "_AT_" + time_str.substring(0,time_str.indexOf(".")).replaceAll(":", "-");
                            FileOutputStream outStream = new FileOutputStream(fileNameToSave);
                            outStream.write((byte[]) msg.contentInBytes);
                            printText("[user][" + msg.sender + "] File received:" + new File(fileNameToSave).getAbsolutePath(), true);
                        }
                    })
                    .match(PrivateGroupTextMessage.class, msg ->
                    {//Get group message and prints it in private message format
                        if (serverActor != null) {
                            printText("[" + msg.group + "][" + msg.sender + "] " + msg.text, true);
                        }
                    })
                    .match(DisconnectMessage.class, msg ->
                    {//Get disconnect message from the server. Leave all groups which the user is in
                        Iterator it = GroupsInformation.entrySet().iterator();
                        LeaveGroupRequestMessage lvMsg = new LeaveGroupRequestMessage();
                        lvMsg.userName = userName;

                        while (it.hasNext()) {
                            Map.Entry pair = (Map.Entry)it.next();
                            lvMsg.groupName = (String)pair.getKey();
                            GroupsInformation.get(lvMsg.groupName).tell(lvMsg, self());
                        }
                        printText(msg.userName + " has disconnected successfully!", false);

                    }).match(CreateGroupApproveMessage.class, msg ->
                    {//Get create group message from the server.
                     //update the maps: add the group to user's groups' map
                     //and to the user's groups' previledges map.
                        GroupsInformation.put(msg.groupName, msg.groupActor);
                        GroupsPreviledges.put(msg.groupName, Previledges.ADMIN);
                        printText(msg.groupName + " created successfully!",false);
                    })
                    .match(AskGroupInvMessage.class, msg ->
                    {//Get an invite to group message from the group and
                    //send an ask message to the target user.
                    //add to group if got Yes or else don't add
                        final Timeout timeout = new Timeout(Duration.create(100, SECONDS));
                        UserInvitationMessage invMsg = new UserInvitationMessage();

                        invMsg.text = "You have been invited to " + msg.groupName + ", Accept?";
                        Future<Object> rt = Patterns.ask(msg.targetActor,invMsg, timeout);
                        try {//wait for the future result and handle result
                            String result = (String) Await.result(rt, timeout.duration());
                            if (result.equals("Yes"))
                            {
                                ActorRef groupActor = GroupsInformation.get(msg.groupName);
                                AddUserToGroupMessage addUMsg = new AddUserToGroupMessage();
                                addUMsg.userToAddActor = msg.targetActor;
                                addUMsg.userToAdd = msg.target;
                                addUMsg.groupName = msg.groupName;
                                addUMsg.groupActor = groupActor;
                                addUMsg.sender = msg.userName;
                                msg.targetActor.tell(addUMsg, ActorRef.noSender());
                                AddUserToGroupMessage addGMsg = new AddUserToGroupMessage();
                                addGMsg.sender = msg.userName;
                                addGMsg.userToAdd = msg.target;
                                addGMsg.userToAddActor = msg.targetActor;
                                groupActor.tell(addGMsg, ActorRef.noSender());

                            }
                            else
                            {
                                if (result.equals("No"))
                                    printText("Group invitation to " + msg.groupName + " was denied by " + msg.target,false);
                                else
                                    printText(msg.target +" answered Group invitation to " + msg.groupName + " by " + result, false);
                            }
                        }
                        catch (Exception e)
                        {
                            printText("It took too many time to " + msg.targetActor+ " to response to the group invitation to "+ msg.groupName, false);
                        }
                    })
                    .match(UserInvitationMessage.class, msg ->
                    {//Get user invitation message and set the input mode for getting an answer
                        printText(msg.text,false);
                        client.setResponseMode(true);
                        client.setInviterActor(sender());
                    })
                    .match(ResponseGroupInvMessage.class, msg ->
                    {//Get response group message and send the answer to the sender(who invited to group)
                        msg.userInviterActor.tell(msg.text, getSelf());
                    })
                    .match(AddUserToGroupMessage.class, msg ->
                    {//Get add user to group message.
                     //update the maps: add the group to user's groups' map
                     //and to the user's groups' previledges map.
                        GroupsInformation.put(msg.groupName, msg.groupActor);
                        GroupsPreviledges.put(msg.groupName, Previledges.USER);

                    })
                    .match(RemoveFromGroupMessage.class, rmvMsg ->
                    {//Get remove from group message from server.
                     //send relevant message to target
                        PrivateGroupTextMessage msg = new PrivateGroupTextMessage();
                        msg.target = rmvMsg.targetName;
                        msg.sender = userName;
                        msg.group = rmvMsg.groupName;
                        msg.text = "You have been demoted to user in " + rmvMsg.groupName + " by " + userName + "!";
                        rmvMsg.targetActor.tell(msg, self());

                    })
                    .match(SendMuteMessage.class, msg ->
                    {//Get mute message from server.
                     //send relevant message to target
                        GetMuteMessage mtUsrMsg = new GetMuteMessage();
                        mtUsrMsg.groupName = msg.groupName;
                        mtUsrMsg.userName = msg.userName;
                        mtUsrMsg.text = "You have been muted for " + msg.time +" seconds in " + msg.groupName +" by " + msg.userName;
                        msg.targetActor.tell(mtUsrMsg, self());

                    })
                    .match(SendUnMuteMessage.class, msg ->
                    {//Get unmute message from server.
                     //send relevant message to target
                        GetUnMuteMessage unMtUsrMsg = new GetUnMuteMessage();
                        unMtUsrMsg.userName = msg.userName;
                        unMtUsrMsg.text = "You have been unmuted!";
                        unMtUsrMsg.timeReason = msg.timeReson;
                        unMtUsrMsg.groupName = msg.groupName;
                        if(msg.timeReson)
                        {//if the unmute is because of time left reason- specify message content
                            unMtUsrMsg.text = "You have been unmuted! Muting time is up!";
                        }
                        msg.targetActor.tell(unMtUsrMsg, self());

                    })
                    .match(GetMuteMessage.class, msg ->
                    {//Get mute message from sender user.
                     //update previledge to mute and print the given message
                        if(GroupsPreviledges.get(msg.groupName) != Previledges.MUTED)
                        {//user is not muted
                            String text = "[" + msg.groupName + "]"+ "[" + msg.userName + "]" + msg.text;
                            printText(text, true);
                            GroupsPreviledges.put(msg.groupName, Previledges.MUTED);
                        }
                        else
                        {//user is already muted from sender user.
                            OtherMessage mtMsg = new OtherMessage();
                            mtMsg.text = msg.userName + " is already muted!";
                            sender().tell(mtMsg, self());
                        }

                    })
                    .match(GetUnMuteMessage.class, msg ->
                    {//Get unmute message from sender user.
                     //update previledge to mute and print the given message
                     //Send approval to unmute message to group
                        if(GroupsPreviledges.get(msg.groupName) == Previledges.MUTED)
                        {
                            GroupsPreviledges.put(msg.groupName, Previledges.USER);
                            String text = "[" + msg.groupName + "]"+ "[" + msg.userName + "]" + msg.text;
                            printText(text, true);
                            UnMuteGroupApproveMessage apvMsg = new UnMuteGroupApproveMessage();
                            apvMsg.userName = userName;
                            GroupsInformation.get(msg.groupName).tell(apvMsg, self());
                        }
                        else if(!msg.timeReason)
                        {//Only if there is a second mute not because of time left reason- print double mute message
                            OtherMessage mtMsg = new OtherMessage();
                            mtMsg.text = msg.userName + " is already unmuted!";
                            sender().tell(mtMsg, self());
                        }
                    })
                    .match(CloseGroupMessage.class, msg -> {
                        if(GroupsInformation.containsKey(msg.groupName) && GroupsPreviledges.containsKey(msg.groupName))
                        {//Get close group message. update map of groups ans send to the group an approval message
                            CloseGroupApproveMessage clsMsg = new CloseGroupApproveMessage();
                            clsMsg.groupName = msg.groupName;
                            clsMsg.userName = userName;
                            GroupsInformation.get(msg.groupName).tell(clsMsg, self());
                            GroupsInformation.remove(msg.groupName);
                            GroupsPreviledges.remove(msg.groupName);
                        }
                    })
                    .match(OtherMessage.class, msg ->
                    {//Get other message- print it
                        printText(msg.text, false);
                    })
                    .match(BroadcastTextMessage.class, msg ->
                    {//Get broadcast message- print it if the user is not in mute and should see this message
                        if((GroupsPreviledges.get(msg.groupName)!= Previledges.MUTED) &&(!(msg.joinBroadcast && userName.equals(msg.userName))))
                            System.out.println(msg.text);
                    })
                    .match(GroupFileMessage.class, msg ->
                    {//Get group file message, save it and print a relevant message
                        String time_str = LocalTime.now().toString();
                        String fileNameToSave = "FROM_" + msg.groupName + "_AT_" + time_str.substring(0,time_str.indexOf(".")).replaceAll(":", "-");
                        FileOutputStream outStream = new FileOutputStream(fileNameToSave);
                        outStream.write((byte[]) msg.contentInBytes);
                        printText("[" + msg.groupName + "][" + msg.userName + "] File received:" + new File(fileNameToSave).getAbsolutePath(), true);
                    })
                    .build();
        }

        @Override
        public void preStart()
        {//When clientActor is first initialized, connection message will be sent to the server if exists.
        if(checkConnection())
        {//If the sever is online(found by the selection in the serverpath)
            ActorSelection server = getContext().actorSelection(serverPath);
            //Send connection message to the server with the client's name.
            ConnectRequestMessage conReqMsg = new ConnectRequestMessage();
            conReqMsg.userName = userName;
            server.tell(conReqMsg, self());
        }
        }

        private void handleInput(String text)
        {//Get the text from the UserInputMessage and handle each text by sending
         // relevant messages.
            String[] inputParams = text.split(" ");

            if (text.equals("/user disconnect"))
            {//Disconnect message input case
               if(checkConnection())
               {//Disconnection message will be sent to the server if exists.
                   //Send disconnection message to the server with the client's name.
                   DisconnectMessage disconnMsg = new DisconnectMessage();
                   disconnMsg.userName = userName;
                   serverActor.tell(disconnMsg, self());
               }
            }

            if(text.startsWith("/user text"))
            {//Private text message input case (1-1 chat).
                if(validInputLength(4, inputParams.length))
                {//Send private text message to another client

                    String targetName = inputParams[2];
                    final Timeout timeout = new Timeout(Duration.create(3, SECONDS));
                    GetUserActorMessage guaMsg = new GetUserActorMessage();
                    guaMsg.targetName = targetName;

                    Future<Object> rt = Patterns.ask(serverActor,guaMsg, timeout);
                    try {//wait for the future result and handle result
                        SendUserActorMessage result = (SendUserActorMessage) Await.result(rt, timeout.duration());
                        if (!result.found)
                        {
                            printText(targetName + " does not exist!", false);
                        }
                        else
                        {
                            PrivateUserTextMessage msg = new PrivateUserTextMessage();
                            msg.sender = userName;
                            msg.target = targetName;
                            msg.text = text.substring(text.indexOf(inputParams[3]));
                            result.targetActor.tell(msg, self());
                        }
                    }
                    catch (Exception e)
                    {
                        printText("It took too many time to find " + targetName, false);
                    }
                }
            }

            else if (text.startsWith("/user file"))
            {//Private file message input case (1-1 chat). Get target actor from server.
             //If target exist, send it the file in a binary format.
                if(validInputLength(4, inputParams.length))
                {//Send private file message to another client
                    final Timeout timeout = new Timeout(Duration.create(3, SECONDS));
                    GetUserActorMessage guaMsg = new GetUserActorMessage();
                    String targetName = inputParams[2];
                    guaMsg.targetName = targetName;

                    Future<Object> rt = Patterns.ask(serverActor,guaMsg, timeout);
                    try {//wait for the future result and handle result
                        SendUserActorMessage result = (SendUserActorMessage) Await.result(rt, timeout.duration());
                        if (!result.found)
                        {
                            printText(targetName + " does not exist!", false);
                        }
                        else
                        {
                            String inputFilePath = text.substring(text.indexOf(inputParams[3]));
                            String filePath = new File("").getAbsolutePath().concat(inputFilePath);
                            File f = new File(filePath);
                            if(!f.exists())
                            {printText(inputFilePath+ " does not exist!", false);}
                            else
                            {
                                try {
                                    BufferedReader bufr = new BufferedReader(new FileReader(f));
                                    PrivateFileMessage msg = new PrivateFileMessage();
                                    msg.sender = userName;
                                    msg.target = targetName;
                                    byte [] bytes = new byte[(int)f.length()];

                                    FileInputStream inputStream = new FileInputStream(f);
                                    inputStream.read(bytes);
                                    inputStream.close();
                                    msg.contentInBytes = bytes;
    //                                msg.contentInBytes = Files.readAllBytes(Paths.get(f.getAbsolutePath()));
                                    msg.sourceFilePath = inputFilePath;
                                    serverActor.tell(msg,self());
                                }
                                catch (Exception e){}
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        printText("It took too many time to find " + targetName, false);
                    }
                }
            }
            else if (text.startsWith("/group send file"))
            {//Private file message input case (1-1 chat). Get target actor from server.
                //If target exist, send it the file in a binary format.
                if(validInputLength(5, inputParams.length)) {//Send private file message to another client
    //                serverActor.tell(msg, self());
                    String inputFilePath = inputParams[4];
                    String filePath = new File("").getAbsolutePath().concat(inputFilePath);
                    File f = new File(filePath);
                    if (!f.exists()) {
                        printText(inputFilePath + " does not exist!", false);
                    } else {
                        try {
                            BufferedReader bufr = new BufferedReader(new FileReader(f));
                            GroupFileMessage msg = new GroupFileMessage();
                            msg.userName = userName;
                            msg.groupName = inputParams[3];
                            msg.sourceFilePath = inputFilePath;
                            byte[] bytes = new byte[(int) f.length()];

                            FileInputStream inputStream = new FileInputStream(f);
                            inputStream.read(bytes);
                            inputStream.close();
                            msg.contentInBytes = bytes;
                            msg.sourceFilePath = inputFilePath;
                            serverActor.tell(msg, self());
                        } catch (Exception e) {
                        }
                    }
                }
            }
            else if(text.startsWith("/group create"))
            {//Create group input case
                if(validInputLength(3, inputParams.length))
                {//Send create group message request to the server
                    CreateGroupRequestMessage msg = new CreateGroupRequestMessage();
                    msg.userName = userName;
                    msg.groupName = text.substring(text.indexOf(inputParams[2]));
                    serverActor.tell(msg, self());
                }
            }
            else if(text.startsWith("/group send text"))
            {//Send message to a specific group input case
                if(validInputLength(5, inputParams.length))
                {//Send group text message to the server
                    GroupMessage msg = new GroupMessage();
                    msg.userName = userName;
                    msg.groupName = inputParams[3];
                    msg.text = text.substring(text.indexOf(inputParams[4]));
                    serverActor.tell(msg, self());
                }
            }
            else if(text.startsWith("/group leave"))
            {//Message of leave a specific group input case
                if(validInputLength(3, inputParams.length))
                {//Send leave group message request to the server
                    LeaveGroupRequestMessage msg = new LeaveGroupRequestMessage();
                    msg.userName = userName;
                    msg.groupName = text.substring(text.indexOf(inputParams[2]));
                    serverActor.tell(msg, self());
                }
            }
            else if(text.startsWith("/group user invite"))
            {//Message of user invite to group input case
                if(validInputLength(5, inputParams.length))
                {//Send a message of user invite to a specific group, to the server.
                    InviteToGroupMessage msg = new InviteToGroupMessage();
                    msg.userName = userName;
                    msg.groupName = inputParams[3];
                    msg.targetName = inputParams[4];
                    serverActor.tell(msg, self());
                }
            }
            else if(text.startsWith("/group user remove"))
            {//Message of user remove from group input case
                if(validInputLength(5, inputParams.length))
                {//Send a message of remove user from a specific group, to the server.
                    RemoveFromGroupMessage msg = new RemoveFromGroupMessage();
                    msg.userName = userName;
                    msg.groupName = inputParams[3];
                    msg.targetName = inputParams[4];
                    serverActor.tell(msg, self());
                }
            }
            else if(text.startsWith("/group coadmin add"))
            {//Message of upgrade user to coadmin in a specific group input case
                if(validInputLength(5, inputParams.length))
                {//Send a message of upgrade user to coadmin in a specific group, to the server.
                    AddCoadminGroupMessage msg = new AddCoadminGroupMessage();
                    msg.userName = userName;
                    msg.userToAdd = inputParams[4];
                    msg.groupName = inputParams[3];
                    serverActor.tell(msg, self());
                }
            }
            else if(text.startsWith("/group coadmin remove"))
            {//Message of downgrade user from coadmin in a specific group input case
                if(validInputLength(5, inputParams.length))
                {//Send a message of downgrade user from coadmin in a specific group, to the se
                    RemoveCoadminGroupMessage msg = new RemoveCoadminGroupMessage();
                    msg.userName = userName;
                    msg.userToRemove = inputParams[4];
                    msg.groupName = inputParams[3];
                    serverActor.tell(msg, self());
                }
            }
            else if(text.startsWith("/group user mute"))
            {//Message of mute another user in a specific group input case
                if(validInputLength(6, inputParams.length))
                {//Send a message of mute another user in a specific group, to the server
                    MuteGroupMessage msg = new MuteGroupMessage();
                    msg.userName = userName;
                    msg.groupName = inputParams[3];
                    msg.target = inputParams[4];
                    try
                    {//The time of muting should be a number, check before cast to int
                        msg.time = Integer.parseInt(inputParams[5]);;
                        serverActor.tell(msg, self());
                    }
                    catch (Exception e)
                    {//Print not a number error to the client
                        printText("time should be a number!",false);
                    }
                }
            }
            else if(text.startsWith("/group user unmute"))
            {//Message of unmute another user in a specific group input case
                if(validInputLength(5, inputParams.length))
                {//Send a message of unmute another user in a specific group, to the server
                    UnMuteGroupMessage msg = new UnMuteGroupMessage();
                    msg.userName = userName;
                    msg.groupName = inputParams[3];
                    msg.target = inputParams[4];
                    serverActor.tell(msg, self());
                }
            }

        }

        private void printText(String text, boolean withTime)
        {//Print the given text with/without the time format according to withTime value
        String message;
        if(withTime)
            message = "[" + LocalTime.now().toString() + "]" + text;
        else
            message = text;
        System.out.println(message);
        }
        private boolean checkConnection()
        {//Return true if the server actor was found in the serverpath or else false
            ActorSelection sel = getContext().getSystem().actorSelection(serverPath);
            Timeout t = new Timeout(5, TimeUnit.SECONDS);
            AskableActorSelection asker = new AskableActorSelection(sel);
            //Wait 5 seconds for the selection to found the serverActor
            Future<Object> fut = asker.ask(new Identify(1), t);
            try
            {   ActorIdentity ident = (ActorIdentity)Await.result(fut, t.duration());
                ActorRef ref = ident.getRef();
                if(ref!=null)
                {//The server was found by the selection in the path
                    return true;
                }
                else
                {//The server was not found by the selection in the path - server is offline
                    System.out.println("server is offline! try again later!");
                    return false;
                }
            }
            catch (Exception e)
            {}
            return false;
        }

        private boolean validInputLength(int requiredLength, int gotLength)
        {//Get required input length number and actual length. if is valid return true
         //or else return false and prints an error message
            if(gotLength<requiredLength)
            {
                printText("invalid input!", false);
                return false;
            }
            return true;
        }
    }
}
