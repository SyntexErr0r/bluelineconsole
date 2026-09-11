package net.nhiroki.bluelineconsole.agent;

import org.junit.Test;
import static org.junit.Assert.*;

import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.AICandidateEntry;
import net.nhiroki.bluelineconsole.commandSearchers.eachSearcher.AICommandSearcher;

public class AgentActionTests {

    @Test
    public void testParseMessageDetails() {
        AgentActionEngine.MessageDetails d1 = AgentActionEngine.parseMessageDetails("faridul sent hi");
        assertEquals("faridul", d1.recipient);
        assertEquals("hi", d1.message);

        AgentActionEngine.MessageDetails d2 = AgentActionEngine.parseMessageDetails("faridul send how are you?");
        assertEquals("faridul", d2.recipient);
        assertEquals("how are you?", d2.message);

        AgentActionEngine.MessageDetails d3 = AgentActionEngine.parseMessageDetails("send meeting at 5pm to faridul");
        assertEquals("faridul", d3.recipient);
        assertEquals("meeting at 5pm", d3.message);

        AgentActionEngine.MessageDetails d4 = AgentActionEngine.parseMessageDetails("faridul: see you tomorrow");
        assertEquals("faridul", d4.recipient);
        assertEquals("see you tomorrow", d4.message);

        AgentActionEngine.MessageDetails d5 = AgentActionEngine.parseMessageDetails("send good morning");
        assertEquals("", d5.recipient);
        assertEquals("good morning", d5.message);

        AgentActionEngine.MessageDetails d6 = AgentActionEngine.parseMessageDetails("faridul");
        assertEquals("faridul", d6.recipient);
        assertEquals("", d6.message);
    }

    @Test
    public void testParseDirectAction() {
        // WhatsApp with user's exact syntax
        AgentActionEngine.Action a1 = AICommandSearcher.parseDirectAction("open whatsapp find faridul sent hi");
        assertNotNull(a1);
        assertEquals("SEND_MESSAGE", a1.type);
        assertEquals("whatsapp", a1.appName.toLowerCase());
        assertEquals("faridul", a1.target);
        assertEquals("hi", a1.query);

        // WhatsApp direct shortcut
        AgentActionEngine.Action a2 = AICommandSearcher.parseDirectAction("whatsapp faridul send hi");
        assertNotNull(a2);
        assertEquals("SEND_MESSAGE", a2.type);
        assertEquals("whatsapp", a2.appName.toLowerCase());
        assertEquals("faridul", a2.target);
        assertEquals("hi", a2.query);

        // With "ai " prefix
        AgentActionEngine.Action a3 = AICommandSearcher.parseDirectAction("ai open whatsapp find faridul sent hi");
        assertNotNull(a3);
        assertEquals("SEND_MESSAGE", a3.type);
        assertEquals("faridul", a3.target);
        assertEquals("hi", a3.query);

        // "send <msg> to <contact> on whatsapp"
        AgentActionEngine.Action a4 = AICommandSearcher.parseDirectAction("send hi to faridul on whatsapp");
        assertNotNull(a4);
        assertEquals("SEND_MESSAGE", a4.type);
        assertEquals("whatsapp", a4.appName.toLowerCase());
        assertEquals("faridul", a4.target);
        assertEquals("hi", a4.query);

        // Telegram
        AgentActionEngine.Action a5 = AICommandSearcher.parseDirectAction("telegram faridul send hi");
        assertNotNull(a5);
        assertEquals("SEND_MESSAGE", a5.type);
        assertEquals("telegram", a5.appName.toLowerCase());
        assertEquals("faridul", a5.target);
        assertEquals("hi", a5.query);

        // YouTube search
        AgentActionEngine.Action a6 = AICommandSearcher.parseDirectAction("youtube lofi beats");
        assertNotNull(a6);
        assertEquals("SEARCH_APP", a6.type);
        assertEquals("YouTube", a6.appName);
        assertEquals("lofi beats", a6.query);

        // Open app
        AgentActionEngine.Action a7 = AICommandSearcher.parseDirectAction("open settings");
        assertNotNull(a7);
        assertEquals("OPEN_APP", a7.type);
        assertEquals("settings", a7.appName);

        // WhatsApp call shortcuts
        AgentActionEngine.Action a8 = AICommandSearcher.parseDirectAction("whatsapp call faridul");
        assertNotNull(a8);
        assertEquals("CALL_APP", a8.type);
        assertEquals("whatsapp", a8.appName.toLowerCase());
        assertEquals("faridul", a8.target);
        assertEquals("voice", a8.query);

        AgentActionEngine.Action a9 = AICommandSearcher.parseDirectAction("call faridul on whatsapp");
        assertNotNull(a9);
        assertEquals("CALL_APP", a9.type);
        assertEquals("whatsapp", a9.appName.toLowerCase());
        assertEquals("faridul", a9.target);
        assertEquals("voice", a9.query);

        AgentActionEngine.Action a10 = AICommandSearcher.parseDirectAction("wa video call faridul");
        assertNotNull(a10);
        assertEquals("CALL_APP", a10.type);
        assertEquals("whatsapp", a10.appName.toLowerCase());
        assertEquals("faridul", a10.target);
        assertEquals("video", a10.query);

        AgentActionEngine.Action a11 = AICommandSearcher.parseDirectAction("open whatsapp call faridul");
        assertNotNull(a11);
        assertEquals("CALL_APP", a11.type);
        assertEquals("whatsapp", a11.appName.toLowerCase());
        assertEquals("faridul", a11.target);

        AgentActionEngine.Action a12 = AICommandSearcher.parseDirectAction("call faridul");
        assertNotNull(a12);
        assertEquals("CALL_APP", a12.type);
        assertEquals("whatsapp", a12.appName.toLowerCase());
        assertEquals("faridul", a12.target);
    }

    @Test
    public void testParseActionFromAIResponse() {
        AgentActionEngine.Action a1 = AICandidateEntry.parseActionFromResponse("Sure, messaging Faridul.\n[ACTION: SEND_MESSAGE, whatsapp, faridul, hi]");
        assertNotNull(a1);
        assertEquals("SEND_MESSAGE", a1.type);
        assertEquals("whatsapp", a1.appName);
        assertEquals("faridul", a1.target);
        assertEquals("hi", a1.query);

        AgentActionEngine.Action a2 = AICandidateEntry.parseActionFromResponse("Opening YouTube\n[ACTION: SEARCH_APP, YouTube, chill hop]");
        assertNotNull(a2);
        assertEquals("SEARCH_APP", a2.type);
        assertEquals("YouTube", a2.appName);
        assertEquals("chill hop", a2.query);

        AgentActionEngine.Action a3 = AICandidateEntry.parseActionFromResponse("Calling Faridul\n[ACTION: CALL_APP, whatsapp, faridul, voice]");
        assertNotNull(a3);
        assertEquals("CALL_APP", a3.type);
        assertEquals("whatsapp", a3.appName);
        assertEquals("faridul", a3.target);
        assertEquals("voice", a3.query);

        AgentActionEngine.Action a4 = AICandidateEntry.parseActionFromResponse("Video calling Faridul\n[ACTION: CALL_APP, whatsapp, faridul, video]");
        assertNotNull(a4);
        assertEquals("CALL_APP", a4.type);
        assertEquals("whatsapp", a4.appName);
        assertEquals("faridul", a4.target);
        assertEquals("video", a4.query);
    }
}
