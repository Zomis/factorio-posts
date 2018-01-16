#!/usr/bin/env groovy

@GrabResolver(name='zomis', root='http://www.zomis.net/maven')
@Grab(group='net.zomis', module='duga-core', version='0.2')
@Grab(group='org.slf4j', module='slf4j-simple', version='1.7.25')
import net.zomis.duga.chat.BotConfiguration
import net.zomis.duga.chat.StackExchangeChatBot
import net.zomis.duga.chat.TestBot
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

class FactorioPosts {

    def duga
    def room

    FactorioPosts(def duga) {
        this.duga = duga
        this.room = duga.room("16134")
    }

    def slurpJson(json) {
        return new JsonSlurper().parseText(json)
    }

def inform(def mod, String title, String body) {
    def modUrl = "https://mods.factorio.com/mods/$mod.owner/$mod.name"
    String firstLine = String.format("**\\[[%s](%s)\\]** **%s**", mod.owner + "/" + mod.name, modUrl, title);
    body = body.split('\n')[0]
    String secondLine = "> $body"
    room.message(firstLine).post()
    room.message(secondLine).post()
}

def updateMod(known, def mod, def msgs) {
    def size = msgs.results.size()
    String key = mod.owner + '/' + mod.name
    if (!known.containsKey(key)) {
        println "updateMod: Known did not contain key $key"
        known[key] = size
    }
    println "updateMod: Comparing for $key: ${known[key]} vs. size $size"
    if (known[key] != size) {
        int lastKnown = known[key]
        for (int i = lastKnown; i < size; i++) {
            println "Inform about msgs $i for $mod.name"
            inform(mod, msgs.results[i].title, msgs.results[i].message)
        }
        known[key] = size
    }
}

def updateReplies(known, def mod, def msg, def replies) {
    def size = replies.results.size()
    def key = mod.name + '/' + msg.id
    if (!known.containsKey(key)) {
        println "updateReplies: Known did not contain key $key"
        known[key] = size
    }
    println "updateReplies: Comparing for $key: ${known[key]} vs. size $size"
    if (known[key] != size) {
        int lastKnown = known[key]
        String modKey = mod.owner + '/' + mod.name
        for (int i = lastKnown; i < size; i++) {
            inform(mod, "Re: " + msg.title, replies.results[i].message)
        }
        known[key] = size
    }
}

def perform(String user, known) {
    println "Fetching data for $user"
    def urlData = "https://mods.factorio.com/api/mods?owner=$user&page_size=100&page=1".toURL().text
    def data = slurpJson(urlData)
//    println "Fetched: $data"
//    println "Fetched data for $user"
    data.results.each { mod ->
        def modName = mod.name
        println "Fetching data for $user / $modName"
        def msgUrlData = "https://mods.factorio.com/api/messages?page_size=50&mod=$modName&page=1&order=oldest".toURL().text
        def msgs = slurpJson(msgUrlData)
//        println "Fetched: $msgs"
        updateMod(known, mod, msgs)
        msgs.results.each { msg ->
            def createdAt = msg.created_at
            def lastReplyAt = msg.last_reply_at
            def header = msg.title
            def body = msg.message
            
            println "Fetching data for $user / $modName / $header"
            def repliesUrlData = "https://mods.factorio.com/api/messages?page_size=100&order=oldest&parent=$msg.id&page=1".toURL().text
            def replies = slurpJson(repliesUrlData)
//            println "Fetched: $replies"
            updateReplies(known, mod, msg, replies)
        }
    }
}

def saveResult(String dataFile, def known) {
    try {
    def builder = new JsonBuilder()
    builder(known)
    println builder.toString()
    def file = new File(dataFile)
    file.write(builder.toString())
    } catch (Exception ex) {
        println "Failing: $ex"
        ex.printStackTrace()
    }
}

def runForUser(String user, String dataFile) {
    println "Run $user with dataFile $dataFile"
    def known = [:]
    def file = new File(dataFile)
    if (file.exists()) {
        println "Datafile exists"
        def json = file.text
        known = slurpJson(json)
        println "Known read from file: $known"
        perform(user, known)
        println "Done with perform."
        println "Known is after perform: $known"
    } else {
        println "Datafile does not exists"
        perform(user, known)
        println "Done with perform."
        println "Known is: $known"
        room.message('Data file did not exist. Existing threads is ' + known).post()
    }
//    saveResult(dataFile, known)
}

    static void main(String[] args) {

        Properties properties = new Properties()
        File propertiesFile = new File('duga.groovy')
        propertiesFile.withInputStream {
            properties.load(it)
        }
        def config = new BotConfiguration()
        config.rootUrl = properties.rootUrl
        config.chatUrl = properties.chatUrl
        config.botEmail = properties.email
        config.botPassword = properties.password

        def duga = new StackExchangeChatBot(config)
        def atomBool = new java.util.concurrent.atomic.AtomicBoolean(false)
        duga.registerListener(net.zomis.duga.chat.events.DugaStartedEvent.class, { e -> atomBool.set(true) })
        duga.start()
        while (!atomBool.get()) {
            Thread.sleep(1000)
        }
        new FactorioPosts(duga).test() //.runForUser('zomis', 'volume/factorio_posts.json')

        duga.stop()
    }
    void test() {
        room.message('This is a test').postNow()
    }

}
