import os
import re
import stat
import time
import logging
from itertools import islice

import Image
import pymongo
from pymongo.objectid import ObjectId

import config

image_directory = config.image_directory
thumbnail_subdir = config.thumbnail_subdir
original_subdir = config.original_subdir
normal_subdir = config.normal_subdir

thumbnail_size = config.thumbnail_size
max_image_file_size = config.max_image_file_size
MAX_THREADS = config.MAX_THREADS

# A post looks like:
# { u'_id': ObjectId('4ce3f2fb4c6b974c3f000000'),
#   u'body': u'hello, world!',
#   u'location': [23.23432, 43.23423'],
#   u'image_id': 1234,
#   u'parent': ObjectId('4ce5a1fb4c6b974c3f000002') }
#
# Both image_id and parent are optional; You can find all
# threads by looking for everything with 'parent':None

class Context(object):
    def __init__(self, database):
        self.conn = pymongo.Connection()
        self.db = self.conn[database]
        self.threads = self.db.threads
        self.threads.ensure_index([('location', pymongo.GEO2D)])
        self.threads.ensure_index('thread_id')
        self.threads.ensure_index('update_timestamp')
        self.__try_make_dir(image_directory)
        self.__try_make_dir(os.path.join(image_directory, thumbnail_subdir))
        self.__try_make_dir(os.path.join(image_directory, original_subdir))
        
    def __try_make_dir(self, dir_name):
        try: os.mkdir(dir_name)
        except: pass

    def make_post(self, body, latitude, longitude, 
                  in_reply_to=None, image_id=None):
        if in_reply_to is None:
            thread_id = ObjectId()
        else:
            query = {'_id': ObjectId(in_reply_to)}
            result = self.threads.find_one(query)
            if result is None:
                return
            thread_id = result['thread_id']
        if image_id is not None:
            image_id = ObjectId(image_id)
        if in_reply_to is not None:
            in_reply_to = ObjectId(in_reply_to)
        timestamp = int(time.time() * 1000)

        # If you want to kick out posts based on longitude/latitude boxes or
        # prevent flooding, etc., here's the place to do it.

        post_id = self.threads.insert({'location': [latitude, longitude],
                                       'body': body,
                                       'parent': in_reply_to,
                                       'image_id': image_id,
                                       'thread_id': thread_id,
                                       'timestamp': timestamp,
                                       'update_timestamp': timestamp})
        logging.debug('Added post #%s' % post_id)

        #If this is a reply, bump the thread
        if in_reply_to is not None:
            search_criteria = {'thread_id': thread_id, 'parent':None}
            update_operation = {'$set': { 'update_timestamp': timestamp }}
            self.threads.update(search_criteria, update_operation)
            logging.debug('Thread bumped')
        return post_id

    def get_posts(self, thread_id):
        query = {'thread_id': ObjectId(thread_id)}
        order = [('_id', pymongo.ASCENDING)]
        return [x for x in self.threads.find(query, sort=order)]

    def get_threads(self, latitude, longitude, sorting):
        latitude = float(latitude)
        longitude = float(longitude)
        order = [('update_timestamp', pymongo.DESCENDING)]
        if sorting == 'local_latest':
            logging.debug('Executing query to get local_latest threads')
            query = {'parent': None, 
                     'location': {'$near': [latitude, longitude]}}
            cursor = islice(self.threads.find(query).limit(100).sort(order), 
                            MAX_THREADS)
        elif sorting == 'global_latest':
            query = {'parent': None}
            cursor = self.threads.find(query).limit(MAX_THREADS).sort(order)
        else: #sorting == closest
            query = {'parent': None,
                     'location': {'$near': [latitude, longitude]}}
            cursor = self.threads.find(query).limit(MAX_THREADS)
        logging.debug('Got cursor, returning results')        
        return [x for x in cursor]

    def kill_thread(self, thread_id):
        query = {'thread_id': ObjectId(thread_id)}
        for post in self.threads.find(query):
            if post['image_id'] is not None:
                self.remove_image(post['image_id'])
            logging.debug('Removing post %s' % post['_id'])
            self.threads.remove(post)

    def remove_image(self, image_id):
        for subdir in [original_subdir, thumbnail_subdir, normal_subdir]:
            path = os.path.join(image_directory, subdir, str(image_id))
            logging.debug('Removing file %s' % path)
            try:
                os.remove(path)
            except:
                logging.error('Problem removing file %s' % path)

    def store_image(self, body, content_type, filename, image_id=None):
        if image_id is None:
            image_id = ObjectId()
        else:
            image_id = ObjectId(image_id)
        original = os.path.join(image_directory, 
                                original_subdir, 
                                str(image_id))
        f = open(original, 'wb')
        f.write(body)
        f.close()

        thumbnail = os.path.join(image_directory, 
                                 thumbnail_subdir,
                                 str(image_id))
        thumb = Image.open(original)
        thumb.thumbnail(thumbnail_size)
        if thumb.mode != 'RGB':
            thumb = thumb.convert('RGB')
        thumb.save(thumbnail, 'JPEG')

        reduced_img = os.path.join(image_directory,
                                   normal_subdir,
                                   str(image_id))
        reduced = Image.open(original)
        if reduced.mode != 'RGB':
            reduced = reduced.convert('RGB')
        reduced_size = os.stat(original)[stat.ST_SIZE]
        quality = 90
        while reduced_size > max_image_file_size and quality > 1: 
            reduced.save(reduced_img, 'JPEG', quality=quality)
            pre_reduced = reduced_size/1024
            reduced_size = os.stat(reduced_img)[stat.ST_SIZE]
            msg = 'Reducing image size from %s KB to %s KB with quality %s'
            logging.debug(msg % (pre_reduced, (reduced_size/1024), quality))
            reduced = Image.open(reduced_img)
            quality /= 2
        reduced.save(reduced_img, 'JPEG')

        return image_id
