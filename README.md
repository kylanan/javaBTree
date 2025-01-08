# javaBTree
Implementation of a B Tree in Java, intended to limit the number of nodes open in memory simultaneously.
load20.txt - file that can be used with the "load" command to load keys 1-20, causing a double split which promotes the root
load210.txt - file that can be used with the "load" command to load keys 1-210, causing two double splits that promote the root

COMMANDS:
Create: Create a new index file
Open: Open an existing index file
Insert: Insert a key-value pair into the current index file
Search: Search the index file for a key
Load: Load a file of comma separated values by inserting each pair into the index file as a key-value pair
  note - Running the "load" command with files with many comma separated values (such as load210.txt) may take a while.
Print: Print out all key-value pairs in the index file
Extract: Exports all of the key-value pairs of the current index file to a new file of comma separated values
Quit: Exit the program
