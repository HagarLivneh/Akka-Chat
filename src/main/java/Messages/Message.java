package Messages;
import akka.actor.*;
import java.io.Serializable;


public class Message implements Serializable
{//Contains all the messages types in the system

    public enum Previledges {ADMIN, COADMIN, USER, MUTED}

    public static class UserInputMessage extends Message
    {//User input text message. The client create it for it's client actor to handle the input text.
     //Contains the text message content
        public String text;
    }

    public static class GetUserActorMessage extends Message
    {//Get user actor message. The client ask the server for the actor who belongs to the target name.
        public String targetName;
    }

    public static class SendUserActorMessage extends Message
    {//Send user actor message. The server response the client for the actor who belongs to the target name he got.
        public ActorRef targetActor;
        public boolean found;
    }

    public static class ConnectRequestMessage extends Message
    {//Connection request message. Sent by the client and server in order to init a connection.
     //Contains the user name and server instance
        public String userName;
        public ActorSelection server;
    }

    public static class DisconnectMessage extends Message
    {//Disconnection message. Sent by the client to the server in order to end a connection.
     //Contains the user name info.
        public String userName;
    }

    public static class PrivateUserTextMessage extends Message
    {//Private user text message. Sent from the sender to the target with the text content.
        public String target;
        public String sender;
        public String text;
    }

    public static class PrivateGroupTextMessage extends Message
    {//Private group text message. Contains the information about the sender and group.
     //sent in order to print message to target in group format.
        public String target;
        public String sender;
        public String group;
        public String text;
    }

    public static class PrivateFileMessage extends Message
    {//Private user file message. Sent from the sender to the target with the file content.
        public String target;
        public String sender;
        public String sourceFilePath;
        public byte[] contentInBytes;
    }

    public static class CreateGroupRequestMessage extends Message
    {//Create group message. Contains the information about the sender and group.
     //This message is sent to the server and group in order to update hit groups info
        public String groupName;
        public String userName;
        public ActorRef userActor;
    }
    public static class CreateGroupApproveMessage extends Message
    {//Create group approval message. Contains the information about the sender and group.
     //This message is sent to the user in order to approve the creation action
        public String groupName;
        public String userName;
        public ActorRef groupActor;
    }

    public static class LeaveGroupRequestMessage extends Message
    {//Leave group message. Contains the information about the sender and group.
     //This message is sent to the server and group in order to approve the leaving group action
        public String groupName;
        public String userName;
    }

    public static class CloseGroupApproveMessage extends Message
    {//Close group approval message. Sent from the user to the soon closed group.
     //Contains the information about the user and group.
        public String groupName;
        public String userName;
    }

    public static class GroupMessage extends Message
    {//Group message. Contains the information about the sender, group and the text content.
     //This message is sent to the server and group. If the message is legal the group will broadcast
     //the text to it's members
        public String groupName;
        public String userName;
        public String text;
    }

    public static class GroupFileMessage extends Message
    {//Group file message. Contains the information about the sender, group and the file content and path.
        public String userName;
        public String groupName;
        public String sourceFilePath;
        public byte[] contentInBytes;
    }

    public static class BroadcastTextMessage extends Message
    {//Broadcast text message. Contains the information about the sender, group and the text content and flag
     //that indicate if the sender will also get this message. If the message is legal the group will broadcast
     //the text to it's members
        public String groupName;
        public String userName;
        public String text;
        public boolean joinBroadcast;
    }

    public static class InviteToGroupMessage extends Message
    {//Invite to group message. Contains the information about the sender, receiver and group.
     //This message is sent to the server and group in order to approve the invitation action
        public String groupName;
        public String userName;
        public String targetName;
        public ActorRef userActor;
        public ActorRef targetActor;
    }

    public static class RemoveFromGroupMessage extends Message
    {//Remove from group message. Contains the information about the sender, receiver and group.
     //This message is sent to the server and group in order to approve the removal action
        public String groupName;
        public String userName;
        public String targetName;
        public ActorRef userActor;
        public ActorRef targetActor;
    }

    public static class AskGroupInvMessage extends Message
    {//Ask Group invitation message. Contains the information about the sender, receiver and group.
     //Sent by the user inviter to the target by ask, waits for Yes/No answer for the invitation.
        public String groupName;
        public String userName;
        public String target;
        public ActorRef targetActor;
        public ActorRef userActor;
    }

    public static class UserInvitationMessage extends Message
    {//User Invitation message. Sent by the user inviter to the target by ask,
     // waits for Yes/No answer for the invitation.
        public String text;
    }

    public static class ResponseGroupInvMessage extends Message
    {//Response user invitation message. Sent from target to inviter after answering the invitation question
        public String text;
        public ActorRef userInviterActor;
    }

    public static class AddUserToGroupMessage extends Message
    {//Add user to group message. Sent from the inviter user to the group and server after the target answered Yes to
     //the invitation. Contains the information about the sender, receiver and group.
        public String userToAdd;
        public String sender;
        public String groupName;
        public ActorRef groupActor;
        public ActorRef userToAddActor;
    }

    public static class AddCoadminGroupMessage extends Message
    {//Add coadmin message. Contains the information about the sender, receiver and group.
     //This message is sent to the server and group in order to approve the add coadmin action
        public String userName;
        public String userToAdd;
        public String groupName;
        public ActorRef userToAddActor;
    }

    public static class RemoveCoadminGroupMessage extends Message
    {//Remove coadmin message. Contains the information about the sender, receiver and group.
     //This message is sent to the server and group in order to approve the remove coadmin action
        public String userName;
        public String userToRemove;
        public String groupName;
        public ActorRef userToRemoveActor;
    }

    public static class MuteGroupMessage extends Message
    {//Mute group message. Contains the information about the sender, receiver and group, time of muting in seconds.
     //This message is sent to the server and group in order to approve the mute action
        public String groupName;
        public String userName;
        public String target;
        public ActorRef targetActor;
        public ActorRef userActor;
        public int time;
    }

    public static class SendMuteMessage extends Message
    {//Send mute message. Contains the information about the sender, receiver and group,
     //and the time of muting in seconds
     //The group sends this message to the muter sender
        public String groupName;
        public String userName;
        public String target;
        public ActorRef targetActor;
        public ActorRef userActor;
        public int time;
    }


    public static class SendUnMuteMessage extends Message
    {//Send unmute message. Contains the information about the sender, receiver and group.
     //If the unmute message was sent because of time left reason or intended unmuting.
     //The group sends this message to the unmuter sender
        public String groupName;
        public String userName;
        public String target;
        public boolean timeReson;
        public ActorRef targetActor;
        public ActorRef userActor;
    }

    public static class UnMuteGroupMessage extends Message
    {//Unmute group message. Contains the information about the sender, receiver and group.
    //This message is sent to the server and group in order to approve the unmute action
        public String groupName;
        public String userName;
        public String target;
        public ActorRef targetActor;
        public ActorRef userActor;
    }

    public static class UnMuteGroupApproveMessage extends Message
    {//Unmute group approval message, the user send it to the group in order to update the group fields
        public String userName;
    }

    public static class GetMuteMessage extends Message
    {//Get mute message that contains mute text, group and muted user names
        public String text;
        public String groupName;
        public String userName;
    }

    public static class GetUnMuteMessage extends Message
    {//Get unMute message that contains unmute text, group and unmuted user names
     //If the unmute message was sent because of time left reason or intended unmuting
        public String text;
        public String groupName;
        public String userName;
        public boolean timeReason;
    }

    public static class CloseGroupMessage extends Message
    {//Close Group message that contains group name
        public String groupName;
    }

    public static class OtherMessage extends Message
    {//Other message that contains text
        public String text;
    }

}
