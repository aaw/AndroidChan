##########################################
# Configuration for the imageboard server
##########################################
image_directory = 'image_store'
thumbnail_subdir = 'thumbnail'
original_subdir = 'original'
normal_subdir = 'normal'

thumbnail_size = (128, 128)
max_image_file_size = 128 * 1024 #128KB
MAX_THREADS = 50 #num threads returned

#####################################
# Configuration for the admin server
#####################################
admin_user = 'admin'
admin_password = 'password'
# Generate cookie_secret once and set the result below. An easy way to generate
# it is: (from a python prompt) 
# >>> import base64
# >>> import uuid
# >>> base64.b64encode(uuid.uuid4().bytes + uuid.uuid4().bytes)
cookie_secret = GENERATE_COOKIE_SECRET_AND_REPLACE_THIS_WITH_IT
