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

import config

logging.basicConfig(level=logging.DEBUG)
contexts = { 'production' : Context('production') }

class BaseHandler(tornado.web.RequestHandler):
    def get_current_user(self):
        return self.get_secure_cookie("user")

class IndexHandler(BaseHandler):
    @tornado.web.authenticated
    def get(self):
        context = contexts['production']
        threads = context.get_threads(latitude=0.0,
                                      longitude=0.0,
                                      sorting='global_latest')
        self.render("templates/index.html", threads=threads)

class ThreadHandler(BaseHandler):
    @tornado.web.authenticated
    def get(self, thread_id):
        context = contexts['production']
        posts = context.get_posts(thread_id)
        self.render("templates/threads.html", thread_id=thread_id, posts=posts)

class BigImageHandler(BaseHandler):
    @tornado.web.authenticated
    def get(self, image_id):
        context = contexts['production']
        self.render("templates/image.html", image_id=image_id)

class ImageBlocker(BaseHandler):
    @tornado.web.authenticated
    def post(self, thread_id, image_id):
        if self.get_argument('cbox', None) is None:
            self.write('unconfirmed')
            return
        in_f = open('static/blocked.jpg', 'rb')
        in_bytes = in_f.read()
        in_f.close()
        context = contexts['production']
        context.store_image(body=in_bytes, content_type='image/jpeg',
                            filename=None, image_id=image_id)
        self.redirect('/threads/%s' % thread_id)

class ThreadKiller(BaseHandler):
    @tornado.web.authenticated
    def post(self, thread_id):
        if self.get_argument('cbox', None) is None:
            self.write('unconfirmed')
            return
        context = contexts['production']
        context.kill_thread(thread_id)
        self.redirect('/')

class LoginHandler(tornado.web.RequestHandler):
    users = {config.admin_user : config.admin_password }

    def get(self):
        next = self.get_argument("next")
        self.write('<html><body><form action="/login" method="post">'
                   'Name: <input type="text" name="name"><br>'
                   'Password: <input type="password" name="password"><br>'
                   '<input type="hidden" name="next" value="%s">'
                   '<input type="submit" value="Sign in">'
                   '</form></body></html>' % next)

    def post(self):
        redirect = self.get_argument("next")
        name = self.get_argument("name")
        password = self.get_argument("password")
        if name in LoginHandler.users and \
           LoginHandler.users[name] == password:
            self.set_secure_cookie("user", self.get_argument("name"))
            self.redirect(redirect)
        else:
            self.write("Invalid password")
        
routes = [(r'/', IndexHandler),
          (r'/threads/([a-fA-F0-9]+)', ThreadHandler),
          (r'/bigimage/([a-fA-F0-9]+)', BigImageHandler),
          (r'/block_image/([a-fA-F0-9]+)/([a-fA-F0-9]+)', ImageBlocker),
          (r'/kill_thread/([a-fA-F0-9]+)', ThreadKiller),
          (r'/login', LoginHandler)
          ]

settings = { 'static_path': os.path.join(os.path.dirname(__file__), 
                                         'image_store'),
             'static_url_prefix' : '/1/img/',
             'login_url' : '/login',
             'cookie_secret' : config.cookie_secret
             }

application = tornado.web.Application(routes, **settings)

if __name__ == "__main__":
    http_server = tornado.httpserver.HTTPServer(application)
    port = 443
    if len(sys.argv) > 1:
        port = int(sys.argv[1])
    http_server.listen(port)
    tornado.ioloop.IOLoop.instance().start()
