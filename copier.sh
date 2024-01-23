#!/bin/zsh

TEST_NAME=fire
cp ~/Projects/personal/haystack/images/"$TEST_NAME".png src/test/resources/"$TEST_NAME"/
cp ~/Projects/personal/haystack/images/"$TEST_NAME"-modified.png src/test/resources/"$TEST_NAME"/
cp ~/Projects/personal/haystack/images/decompressed_data_bytes src/test/resources/"$TEST_NAME"/
cp ~/Projects/personal/haystack/images/compressed_data_bytes src/test/resources/"$TEST_NAME"/
cp ~/Projects/personal/haystack/images/filtered_data_bytes src/test/resources/"$TEST_NAME"/
cp ~/Projects/personal/haystack/images/unfiltered_data_bytes src/test/resources/"$TEST_NAME"/

# create folder testOutput if it does not exist
mkdir -p src/test/resources/"$TEST_NAME"/testOutput
