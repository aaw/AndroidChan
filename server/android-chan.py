import uuid
import json
import os
import sys
import logging

import tornado.httpserver
import tornado.ioloop
import tornado.web

from mongo_context import Context, image_directory
from pymongo.json_util import default as mdefault

logging.basicConfig(level=logging.DEBUG)
contexts = { 'production' : Context('production') }

# /posts/id
class PostHandler(tornado.web.RequestHandler):
    def get(self, thread_id):
        context = contexts['production']
        self.write(json.dumps(context.get_posts(thread_id), default=mdefault))

    def post(self, post_id):
        message = self.get_argument('body', '')
        image = self.get_argument('image', None)
        latitude = float(self.get_argument('latitude'))
        longitude = float(self.get_argument('longitude'))
        context = contexts['production']
        context.make_post(body=message, 
                          longitude=longitude,
                          latitude=latitude,
                          in_reply_to=post_id, 
                          image_id=image) 

class ImageHandler(tornado.web.RequestHandler):
    # get is handled by static_path/static_url_prefix in
    # tornado startup settings

    def post(self):
        context = contexts['production']
        image_id = context.store_image(**self.request.files['image'][0])
        self.write(json.dumps({'_id' : str(image_id)}, default=mdefault))

# /threads
class ThreadHandler(tornado.web.RequestHandler):
    def get(self):
        context = contexts['production']
        latitude = self.get_argument('latitude')
        longitude = self.get_argument('longitude')
        sorting = self.get_argument('sort', 'local_latest')
        self.write(json.dumps(context.get_threads(latitude=latitude,
                                                  longitude=longitude,
                                                  sorting=sorting),
                              default=mdefault))

    def post(self):
        message = self.get_argument('body', '')
        image = self.get_argument('image', None)
        latitude = float(self.get_argument('latitude'))
        longitude = float(self.get_argument('longitude'))
        context = contexts['production']
        context.make_post(body=message, 
                          longitude=longitude,
                          latitude=latitude,
                          in_reply_to=None, 
                          image_id=image)

routes = [(r'/1/threads', ThreadHandler),
          (r'/1/img', ImageHandler),
          (r'/1/posts/([a-fA-F0-9]+)', PostHandler)
          ]

settings = { 'static_path': os.path.join(os.path.dirname(__file__), 
                                         'image_store'),
             'static_url_prefix' : '/1/img/'}

application = tornado.web.Application(routes, **settings)

if __name__ == "__main__":
    http_server = tornado.httpserver.HTTPServer(application)
    port = 80
    if len(sys.argv) > 1:
        port = int(sys.argv[1])
    http_server.listen(port)
    tornado.ioloop.IOLoop.instance().start()
