# jedit_interlis_plugin

```
java -jar jedit.jar -log=3
```

```
./gradlew clean build copyPlugin -i && rm ~/Library/jEdit/jars-cache/InterlisPlugin.jar.summary
```

## notes

- TdCache funktioniert nur, wenn Modell gültig ist, da sond td==null ist.


## todo

- ~~meta info in props~~
- ~~dependency handling?~~
- Keyboard shortcut (default): Geht bei mir noch nicht. Ist es überhaupt sinnvoll? Oder soll man das den Benutzer entscheiden lassen.
- ~~activate plugin only when .ili file in current buffer?~~
- ~~console output?~~ 
- ~~error list?~~ 
-~~ toggle (is_selected)~~
- options: ~~ilidirs!!~~
- ~~Fokus sollte nach kompilieren nicht in der Shell sein.~~
- outline/tree: icons (was hat umleditor?)
- outline/tree: ~~~imported models~~ (sonst noch was sinnvolles?).
- ~~outline/tree: fehlermeldung in der konsole?~~
- ~~cache für td~~
- ~~autocomplete: INTERLIS 2.4 <shift+enter> -> Kommentare~~
- ~~autocomplete: MODEL .. <shift+enter> -> oberhalb die Metattribute~~
- autocomplete: ~~TOPIC, CLASS,~~ ASSOCIATION
- Deployment? Wohin? Automatisch?
- ~~Default indent size: https://chat.qwen.ai/c/7e6814f6-b48d-4678-84e1-78e8cea0fe37~~
- Edit: Modelle zum Importieren vorschlagen 
- Hyperlink: Auch für Topics, Klassen etc. (weiss nicht wie schwierig das ist).
- UML: als dockable window.