This code was adapted from the TaggedImageStorageMultipageTiff system from
MicroManager 1.x, which means that it mostly works with TaggedImages and uses
JSONObject an awful lot -- call this "the old data model", while the 
Image/Metadata/SummaryMetadata/DisplaySettings are "the new data model". These
two models disagree on several points, which means that this code has to
jump through some hoops when converting from the old model to the new one (and
vice versa). In particular:

 * In the old model, display settings and image comments and acquisition
   comments are stored in the same set of JSON. In the new model, acquisition
   comments are in the SummaryMetadata, image comments are in the Metadata,
   and DisplaySettings are their own thing.
 * In the old model, certain image properties like width, height, and pixel
   type are stored in the summary metadata. In the new model, these are
   inherent properties of the Image class. This means that whenever we want
   to write out an old-style summary metadata JSON, we need an image available
   to provide the missing fields -- and when we want to read off an Image,
   we need a properly-populated summary metadata JSON object to read the
   image properties from.
