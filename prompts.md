So let's get back to the auto closer. Please write it w/o ast with the following features and use cases for the CLASS element (at the moment). Following are the cases that should work: 

"CLASS foo ="
"CLASS (ABSTRACT) foo ="
"CLASS (EXTENDED) foo ="
"CLASS (FINAL) foo ="
"CLASS foo EXTENDS bar ="

The use case with the braces can be combined and mixed. The order of the properties inside the braces is not relevant.

"CLASS (ABSTRACT, EXTENDED) foo ="

All the words / elements can be on different lines and there can be multiple whitespaces between the words.

I will add rules for VIEW, TOPIC, STRUCTURE later. So please reflect this in your implementation. You can write even a small parses with some rules. 


---------


How could be a regex free solution look like? Also without the ast index of the mode. B/c this won't work since it's always out of sync with the buffer. E.g. how would you implement a tokenizer?


---------


The very same rules apply to "STRUCTURE".


---------


Can you please fix the indentation? If the Header starts with e.g. an indentation of 3 whitespaces the closing text should too.


---------


Ok, let's implement TOPIC. These are the cases:

"TOPIC foo ="
"TOPIC (ABSTRACT) foo ="
"TOPIC (FINAL) foo ="
"TOPIC foo EXTENDS bar ="

The properties in the braces can be combined and the order does not matter. Properties can be combined with EXTENDS:

"TOPIC (ABSTRACT) foo EXTENDS bar="

EXTENDED cannot be used.

A topic can also be something like a view. The syntax is:

"VIEW TOPIC foo =". 

But in this case there is "DEPENDS ON" following the "=" sign. So please add these two strings on a new line, like:

"
VIEW TOPIC foo =
DEPENDS ON
END foo;
"


---------



Ok, this works so far very nicelly. I noticed that I really would like to add a empty line betwenn the header and the end, like:

"
CLASS foo =

END foo;
"

And please put the cursor on this line.


--------


Does not work. The cursor is just after "=". But it should be on the new line.


--------


Much better. Please adjust the indentation of the cursor. Use the same as for the header and the end.


--------


Ok, thanks. For "VIEW TOPIC" the cursor should be just on whitespace after "DEPENDS ON"


--------
