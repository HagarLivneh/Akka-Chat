package Server;

import akka.actor.*;
import Messages.Message.*;
import java.util.HashMap;

public class ServerActor extends AbstractActor
{
    //Map of username-ClientActorRef. Includes every user that is connected to the server.
    HashMap<String, ActorRef> usersInformation = new HashMap<String, ActorRef>();
    //Map of groupname-GroupActorRef. Includes every group which exists in the server.
    //(created and not yet closed)
    HashMap<String, ActorRef> groupsInformation = new HashMap<String, ActorRef>();

    @Override
    public Receive createReceive()
    {//Handle incoming messages of the ServerActor's mailbox
        return receiveBuilder()
                .match(ConnectRequestMessage.class, connMsg ->
                {//Get connection request and init connection client
                    if(!usersInformation.containsKey(connMsg.userName))
                    {//username is not in use
                        ActorRef clientActor = sender();
                        ActorSelection serverActor = getContext().actorSelection("akka.tcp://System@127.0.0.1:8000/user/Server");
                        usersInformation.put(connMsg.userName, clientActor);

                        //send connection message to the client
                        ConnectRequestMessage connectMessage = new ConnectRequestMessage();
                        connectMessage.server = serverActor;
                        clientActor.tell(connectMessage, self());

                        //send connection request to the server
//                        connectMessage.client = clientActor;
//                        serverActor.tell(connectMessage, self());
                    }
                    else
                    {//Server holds user name in its users map, user name is in use
                        OtherMessage msg = new OtherMessage();
                        msg.text = connMsg.userName +" is in use!";
                        sender().tell(msg, ActorRef.noSender());
                    }
                 })
                .match(DisconnectMessage.class, disconnMsg ->
                {//Get disconnection request and remove connected user
                    ActorRef clientActor;
                    if(!usersInformation.containsKey(disconnMsg.userName))
                    {//User is not in connected users list - user is already disconnected/ doesn't exist
                        clientActor = sender();
                        OtherMessage msg = new OtherMessage();
                        msg.text = disconnMsg.userName + " is already disconnected/ doesn't exist";
                        clientActor.tell(msg, ActorRef.noSender());
                    }
                    else
                    {//Remove user from connected users list and sent disconnect message to the user
                        usersInformation.remove(disconnMsg.userName);
                        clientActor = sender();
                        clientActor.tell(disconnMsg, ActorRef.noSender());
//                        clientActor.tell(akka.actor.PoisonPill.getInstance(), self());
                    }
                    })
                .match(GetUserActorMessage.class, guaMsg ->
                {//Get user private text message and forwards it to the target user
                    SendUserActorMessage msg = new SendUserActorMessage();
                    msg.targetActor = usersInformation.get(guaMsg.targetName);
                    if(!usersInformation.containsKey(guaMsg.targetName))
                        msg.found = false;
                    else
                        msg.found = true;
                    getSender().tell(msg, getSelf());
                })
                .match(PrivateGroupTextMessage.class, prvgMsg ->
                {//Get a private group text message and forwards it to the relevant user
                    if(!userNotExistMessage(prvgMsg.target, sender()))
                    {
                        ActorRef target = usersInformation.get(prvgMsg.target);
                        target.forward(prvgMsg, getContext());
                    }
                })
                .match(PrivateFileMessage.class, prvfMsg ->
                {//Get user private file message and forwards it to the target user
                    if(!userNotExistMessage(prvfMsg.target, sender()))
                    {
                        ActorRef target = usersInformation.get(prvfMsg.target);
                        target.forward(prvfMsg, getContext());
                    }
                })
                .match(CreateGroupRequestMessage.class, crtGrpMsg ->
                {//Get create group message, create it if group is not in groups list
                    if(groupsInformation.containsKey(crtGrpMsg.groupName))
                    {
                        OtherMessage msg = new OtherMessage();
                        msg.text = crtGrpMsg.groupName + " already exists!";
                        sender().tell(msg, ActorRef.noSender());
                    }
                    else
                    {//create new groupActor, add it to groups list and send relevant message to the user created it.
                        ActorRef groupActor = getContext().actorOf(Props.create(GroupActor.class, crtGrpMsg.groupName), "Group" + crtGrpMsg.groupName);
                        groupsInformation.put(crtGrpMsg.groupName, groupActor);

                        crtGrpMsg.userActor = usersInformation.get(crtGrpMsg.userName);
                        groupActor.forward(crtGrpMsg, getContext());

                        CreateGroupApproveMessage msg = new CreateGroupApproveMessage();
                        msg.groupActor = groupActor;
                        msg.userName = crtGrpMsg.userName;
                        msg.groupName = crtGrpMsg.groupName;
                        sender().tell(msg, ActorRef.noSender());
                    }
                })
                .match(LeaveGroupRequestMessage.class, lvGrpMsg ->
                {//Get a leave group message, if group exist forward the message to the group
                    if(!groupNotExistMessage(lvGrpMsg.groupName, sender()))
                    {
                        ActorRef groupActor = groupsInformation.get(lvGrpMsg.groupName);
                        groupActor.forward(lvGrpMsg, getContext());
                    }
                })
                .match(GroupMessage.class, grpMsg ->
                {//Get group text message and forwards it to the target groupActor
                    if(!groupNotExistMessage(grpMsg.groupName, sender()))
                    {
                        ActorRef groupActor = groupsInformation.get(grpMsg.groupName);
                        groupActor.forward(grpMsg, getContext());
                    }
                })
                .match(GroupFileMessage.class, grpfMsg ->
                {//Get group text message and forwards it to the target groupActor
                    if(!groupNotExistMessage(grpfMsg.groupName, sender()))
                    {
                        ActorRef groupActor = groupsInformation.get(grpfMsg.groupName);
                        groupActor.forward(grpfMsg, getContext());
                    }
                })
                .match(InviteToGroupMessage.class, invMsg ->
                {//Get invite message from user, check if user, target, group exist and forward message to group
                    if(!groupNotExistMessage(invMsg.groupName, sender()) && !userNotExistMessage(invMsg.targetName, sender()))
                    {
                        ActorRef groupActor = groupsInformation.get(invMsg.groupName);
                        invMsg.userActor = sender();
                        invMsg.targetActor = usersInformation.get(invMsg.targetName);
                        groupActor.forward(invMsg, getContext());
                    }
                })
                .match(RemoveFromGroupMessage.class, rmvMsg ->
                {//Get remove message from user, check if user, target, group exist and forward message to group
                    if(!groupNotExistMessage(rmvMsg.groupName, sender()) && !userNotExistMessage(rmvMsg.targetName, sender()))
                    {
                        ActorRef groupActor = groupsInformation.get(rmvMsg.groupName);
                        rmvMsg.userActor = sender();
                        rmvMsg.targetActor = usersInformation.get(rmvMsg.targetName);
                        groupActor.forward(rmvMsg, getContext());
                    }
                })
                .match(CloseGroupMessage.class, clsGrpMsg ->
                {//Get close group message, tell it to group and remove group from groups list
                    if(!groupNotExistMessage(clsGrpMsg.groupName, sender()))
                    {
                        groupsInformation.get(clsGrpMsg.groupName).tell(akka.actor.PoisonPill.getInstance(), self());
                        groupsInformation.remove(clsGrpMsg.groupName);
                    }
                })
                .match(AddCoadminGroupMessage.class, addcoMsg ->
                {//Get add coadmin message, update target user actor and forward it to group
                    if(!groupNotExistMessage(addcoMsg.groupName, sender()) && !userNotExistMessage(addcoMsg.userToAdd, sender()))
                    {
                        ActorRef groupActor = groupsInformation.get(addcoMsg.groupName);
                        addcoMsg.userToAddActor = usersInformation.get(addcoMsg.userToAdd);
                        groupActor.forward(addcoMsg, getContext());
                    }

                })
                .match(RemoveCoadminGroupMessage.class, rmvcoMsg ->
                {//Get remove coadmin message, update target user actor and forward it to group
                    if(!groupNotExistMessage(rmvcoMsg.groupName, sender()) && !userNotExistMessage(rmvcoMsg.userToRemove, sender()))
                    {
                        ActorRef groupActor = groupsInformation.get(rmvcoMsg.groupName);
                        rmvcoMsg.userToRemoveActor = usersInformation.get(rmvcoMsg.userToRemove);
                        groupActor.forward(rmvcoMsg, getContext());
                    }
                })
                .match(MuteGroupMessage.class, mtMsg ->
                {//Get mute message, update target user actor and sender actor and forward it to group
                    if(!groupNotExistMessage(mtMsg.groupName, sender()) && !userNotExistMessage(mtMsg.target, sender()))
                    {
                        ActorRef groupActor = groupsInformation.get(mtMsg.groupName);
                        mtMsg.userActor = sender();
                        mtMsg.targetActor = usersInformation.get(mtMsg.target);
                        groupActor.forward(mtMsg, getContext());
                    }
                }).match(UnMuteGroupMessage.class, unmtMsg ->
                {//Get unmute message, update target user actor and sender actor and forward it to group
                    if(!groupNotExistMessage(unmtMsg.groupName, sender()) && !userNotExistMessage(unmtMsg.target, sender()))
                    {
                        ActorRef groupActor = groupsInformation.get(unmtMsg.groupName);
                        unmtMsg.userActor = sender();
                        unmtMsg.targetActor = usersInformation.get(unmtMsg.target);
                        groupActor.forward(unmtMsg, getContext());
                    }
                }).build();
    }

    private boolean userNotExistMessage(String userName, ActorRef userActor)
    {//TODO: make it 1 func with boolean identifier
        if(usersInformation.containsKey(userName))
            return false;
        SendNotExistMessage(userName, userActor);
        return true;
    }

    private boolean groupNotExistMessage(String groupName, ActorRef userActor)
    {
        if(groupsInformation.containsKey(groupName))
            return false;
        SendNotExistMessage(groupName, userActor);
        return true;
    }

    private void SendNotExistMessage(String missing, ActorRef targetActor)
    {//Send info not exist message to the given user actor
        OtherMessage msg  = new OtherMessage();
        msg.text = missing + " does not exist!";
        targetActor.tell(msg, ActorRef.noSender());
    }
}
