#!/usr/bin/env groovy

@Library('ZomisJenkins')
import net.zomis.jenkins.Duga
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

@NonCPS
def slurpJson(json) {
  return new JsonSlurper().parseText(json)
}

def dataFile = '/home/zomis/jenkins/factorio_posts.json'

@NonCPS
def inform(def mod, String title, String body) {
    def modUrl = "https://mods.factorio.com/mods/$mod.owner/$mod.name"
    String firstLine = String.format("**\\[[%s](%s)\\]** **%s**", mod.owner + "/" + mod.name, modUrl, title);
    body = body.split('\n')[0]
    String secondLine = "> $body"
    def duga = new Duga()
    duga.dugaResult(firstLine)
    duga.dugaResult(secondLine)
}

@NonCPS
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

@NonCPS
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

@NonCPS
def perform(String user, known) {
    def urlData = "https://mods.factorio.com/api/mods?owner=$user&page_size=100&page=1".toURL().text
    def data = slurpJson(urlData)
    data.results.each { mod ->
        def modName = mod.name
        def msgUrlData = "https://mods.factorio.com/api/messages?page_size=50&mod=$modName&page=1&order=oldest".toURL().text
        def msgs = slurpJson(msgUrlData)
        updateMod(known, mod, msgs)
        msgs.results.each { msg ->
            def createdAt = msg.created_at
            def lastReplyAt = msg.last_reply_at
            def header = msg.title
            def body = msg.message

            def repliesUrlData = "https://mods.factorio.com/api/messages?page_size=100&order=oldest&parent=$msg.id&page=1".toURL().text
            def replies = slurpJson(repliesUrlData)
            updateReplies(known, mod, msg, replies)
        }
    }
}
/*
properties(
    [
        pipelineTriggers([cron('0 20 * * *')])
    ]
)
*/
node {
    def duga = new Duga()
    def known = [:]
    if (fileExists(dataFile)) {
        def json = sh(returnStdout: true, script: "cat $dataFile")
        known = slurpJson(json)
        println "Known read from file: $known"
        perform('zomis', known)
        println "Known is after perform: $known"
    } else {
        perform('zomis', known)
        println "Known is: $known"
        duga.dugaResult('Data file did not exist. Existing threads is ' + known)
    }
    def builder = new JsonBuilder()
    builder(known)
    println builder.toString()
    def file = new File(dataFile)
    file.write(builder.toString())

}
